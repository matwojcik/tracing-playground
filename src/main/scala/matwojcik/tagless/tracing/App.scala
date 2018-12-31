package matwojcik.tagless.tracing
import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.effect.{Async, Concurrent, ExitCode, IO, IOApp, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import matwojcik.tagless.tracing.akkahttp.AkkaHttpServer
import matwojcik.tagless.tracing.http4s.Http4sServer
import org.http4s.server.Server

import scala.concurrent.ExecutionContext

object App extends IOApp with StrictLogging {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(5))
  implicit def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.unsafeCreate[F]
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val remoteCaller: RemoteCaller[IO] = RemoteCaller.akkaHttp

  val http4sServer: HttpServer[IO, Server[IO]] = Http4sServer.instance(new Http4sServer)
  val akkaHttpServer: HttpServer[IO, Http.ServerBinding] = AkkaHttpServer.instance(new AkkaHttpServer(Something[IO], RemoteCaller.akkaHttp))

  override def run(args: List[String]): IO[ExitCode] =
    Program.program(http4sServer)(Id("123"))

}

object Program {

  import cats.syntax.all._

  def program[F[_]: Async: RemoteCaller: DeferFuture, T](server: HttpServer[F, T])(id: Id): F[ExitCode] =
    buildAndTrigger(server, id).use(_ => Async[F].never[Unit]).as(ExitCode.Success)

  private def buildAndTrigger[T, F[_]: Async: RemoteCaller: DeferFuture](
    server: HttpServer[F, T],
    id: Id
  ): Resource[F, Unit] =
    for {
      _ <- server.build
      _ <- Resource.liftF(RemoteCaller[F].callRemote(id, s"http://localhost:8080/trigger/${id.value}"))
    } yield ()
}

case class Id(value: String) extends AnyVal
