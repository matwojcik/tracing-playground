package matwojcik.tagless.tracing
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.Parallel
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import matwojcik.tagless.tracing.akkahttp.AkkaHttpServer
import matwojcik.tagless.tracing.http4s.Http4sServer
import org.http4s.server.Server

import scala.concurrent.ExecutionContext
import scala.io.StdIn

object App extends IOApp with StrictLogging {

  implicit def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.unsafeCreate[F]

  val ecc = new ExecutionContextAware(ExecutionContext.global)

  override implicit protected val contextShift: ContextShift[IO] = IO.contextShift(ecc)
  override implicit protected val timer: Timer[IO] = IO.timer(ecc)

  val ecResource = Resource
    .make(IO(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())))(ec => IO(ec.shutdown()))
    .map(new ExecutionContextAware(_))

  implicit val system: Resource[IO, ActorSystem] = Resource.make(IO(ActorSystem()))(sys => IO.fromFuture(IO(sys.terminate())).void)

  override def run(args: List[String]): IO[ExitCode] =
    (system, ecResource).tupled.use { case (sys, ec) =>
      implicit val system = sys
      implicit val ecc = ec
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      implicit val remoteCaller: RemoteCaller[IO] = RemoteCaller.akkaHttp

      val http4sServer: HttpServer[IO, Server[IO]] = Http4sServer.instance(new Http4sServer)
      val akkaHttpServer: HttpServer[IO, Http.ServerBinding] =
        AkkaHttpServer.instance(new AkkaHttpServer(Something[IO], RemoteCaller.akkaHttp))

      Program.program(akkaHttpServer)(3).race(IO(StdIn.readLine()))
    }.as(ExitCode.Success)

}

object Program {

  import cats.syntax.all._

  def program[F[_]: Async: RemoteCaller: DeferFuture, G[_], T](server: HttpServer[F, T])(amountOfCalls: Int)(implicit p: Parallel[F, G]): F[Nothing] =
    buildAndTrigger(server, amountOfCalls).use[Nothing](_ => Async[F].never)

  private def buildAndTrigger[T, F[_]: Async: RemoteCaller: DeferFuture, G[_]](
    server: HttpServer[F, T],
    amountOfCalls: Int
  )(implicit p: Parallel[F, G]): Resource[F, Unit] =
    for {
      _ <- server.build
      ids = (1 to amountOfCalls).map(i => Id(i.toString)).toList
      _ <- Resource.liftF(ids.map(id => RemoteCaller[F].callRemote(id, s"http://localhost:8080/trigger/${id.value}")).parSequence)
    } yield ()
}

case class Id(value: String) extends AnyVal
