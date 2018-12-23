package matwojcik.tagless.tracing
import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import matwojcik.tagless.tracing.akkahttp.AkkaHttpServer

import scala.concurrent.ExecutionContext

object App extends IOApp with StrictLogging {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(5))
  implicit def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.unsafeCreate[F]
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val server = new AkkaHttpServer(Something[IO], RemoteCaller.akkaHttp)
  implicit val httpServer: HttpServer[IO] = AkkaHttpServer.instance(server)
  implicit val remoteCaller: RemoteCaller[IO] = RemoteCaller.akkaHttp

  override def run(args: List[String]): IO[ExitCode] =
    Program.program[IO](Id("123"))

}

object Program {

  def program[F[_]: Sync: RemoteCaller: DeferFuture: HttpServer](id: Id): F[ExitCode] =
    for {
      _ <- HttpServer[F].build
      _ <- RemoteCaller[F].callRemote(id, s"http://localhost:8080/trigger/${id.value}")
    } yield ExitCode.Success

}

case class Id(value: String) extends AnyVal
