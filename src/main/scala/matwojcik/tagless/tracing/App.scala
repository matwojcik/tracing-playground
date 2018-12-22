package matwojcik.tagless.tracing
import java.util.concurrent.ForkJoinPool

import cats.effect.ContextShift
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object App extends IOApp with StrictLogging {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(5))
  implicit def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.unsafeCreate[F]

  override def run(args: List[String]): IO[ExitCode] =
    Program.program[IO](new Something[IO])

}

object Program extends StrictLogging {

  def program[F[_]: Sync](something: Something[F])(implicit CS: ContextShift[F], ec: ExecutionContext): F[ExitCode] =
    for {
      _      <- something.functionWithALotOfLogging(Id("1"))
      result <- Sync[F].pure(ExitCode.Success)
    } yield result

}

case class Id(value: String) extends AnyVal
