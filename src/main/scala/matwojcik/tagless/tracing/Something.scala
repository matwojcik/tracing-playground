package matwojcik.tagless.tracing
import cats.effect.ContextShift
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.tagless.finalAlg
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@finalAlg
trait Something[F[_]] {
  def functionWithALotOfLogging(id: Id): F[Unit]
}

object Something {
  implicit def instance[F[_]: Sync: DeferFuture: Logger: ContextShift](implicit executionContext: ExecutionContext): Something[F] = new Something[F] {

    def functionWithALotOfLogging(id: Id): F[Unit] =
      for {
        _ <- Logger[F].info(s"Entered functionWithALotOfLogging, $id")
        _ <- Sync[F].delay(ImpureFunctions.logging(id))
        _ <- Logger[F].info(s"Back after impure function logging, $id")
        _ <- DeferFuture[F].defer(ImpureFunctions.futureLogging(id))
        _ <- Logger[F].info(s"Back after future logging, $id")
        _ <- ContextShift[F].evalOn(executionContext)(Logger[F].info(s"Logging in another ec, $id"))
        _ <- Logger[F].info(s"Back after logging in another ec, $id")
      } yield ()
  }
}

object ImpureFunctions extends StrictLogging {
  def logging(id: Id): Unit = logger.info(s"Impure logging, $id")

  def futureLogging(id: Id)(implicit ec: ExecutionContext): Future[Unit] = Future {
    logger.info(s"Logging in future, $id")
  }
}
