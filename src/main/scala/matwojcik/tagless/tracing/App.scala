package matwojcik.tagless.tracing
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.Parallel
import cats.effect.{Async, ExitCode, IO, IOApp, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.instances.list._
import cats.syntax.parallel._
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import matwojcik.tagless.tracing.akkahttp.AkkaHttpServer
import matwojcik.tagless.tracing.http4s.Http4sServer
import org.http4s.server.Server

import scala.concurrent.ExecutionContext

object App extends IOApp with StrictLogging {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.unsafeCreate[F]
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val remoteCaller: RemoteCaller[IO] = RemoteCaller.akkaHttp

  val http4sServer: HttpServer[IO, Server[IO]] = Http4sServer.instance(new Http4sServer)
  val akkaHttpServer: HttpServer[IO, Http.ServerBinding] = AkkaHttpServer.instance(new AkkaHttpServer(Something[IO], RemoteCaller.akkaHttp))

  override def run(args: List[String]): IO[ExitCode] =
    Program.program(akkaHttpServer)(3)

}

object Program {

  import cats.syntax.all._

  def program[F[_]: Async: RemoteCaller: DeferFuture, G[_], T](server: HttpServer[F, T])(amountOfCalls: Int)(implicit p: Parallel[F,G]): F[ExitCode] =
    buildAndTrigger(server, amountOfCalls).use(_ => Async[F].never[Unit]).as(ExitCode.Success)

  private def buildAndTrigger[T, F[_]: Async: RemoteCaller: DeferFuture, G[_]](
    server: HttpServer[F, T],
    amountOfCalls: Int
  )(implicit p: Parallel[F,G]): Resource[F, Unit] =
    for {
      _ <- server.build
      ids = (1 to amountOfCalls).map(i => Id(i.toString)).toList
      _ <- Resource.liftF(ids.map(id => RemoteCaller[F].callRemote(id, s"http://localhost:8080/trigger/${id.value}")).parSequence)
    } yield ()
}

case class Id(value: String) extends AnyVal
