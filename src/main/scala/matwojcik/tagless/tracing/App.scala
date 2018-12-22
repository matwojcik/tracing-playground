package matwojcik.tagless.tracing
import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
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
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val something = new Something[IO]
  val server = new HttpServer(something, RemoteCaller[IO])

  override def run(args: List[String]): IO[ExitCode] =
    Program.program[IO](server)(Id("123"))

}

object Program {

  def program[F[_]: Sync: RemoteCaller: DeferFuture](
    httpServer: HttpServer
  )(id: Id
  )(implicit CS: ContextShift[F],
    ec: ExecutionContext,
    actorSystem: ActorSystem,
    materializer: ActorMaterializer
  ): F[ExitCode] =
    for {
      _      <- DeferFuture[F].defer(Http().bindAndHandle(httpServer.route, "localhost", 8080))
      _      <- RemoteCaller[F].callRemote(id, s"http://localhost:8080/trigger/${id.value}")
      result <- Sync[F].pure(ExitCode.Success)
    } yield result

}

case class Id(value: String) extends AnyVal
