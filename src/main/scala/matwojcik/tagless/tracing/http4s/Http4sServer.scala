package matwojcik.tagless.tracing.http4s
import cats.Functor
import cats.effect.{ConcurrentEffect, IO, LiftIO, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import kamon.http4s.middleware.server.KamonSupport
import matwojcik.tagless.tracing.HttpServer
import matwojcik.tagless.tracing.Id
import matwojcik.tagless.tracing.RemoteCaller
import matwojcik.tagless.tracing.Something
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

class Http4sServer[F[_]: Something: RemoteCaller: Sync: Logger] extends Http4sDsl[F] {

  val service: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "hello" / id =>
      Something[F].functionWithALotOfLogging(Id(id)).flatMap(_ => Ok("Hello world"))
    case GET -> Root / "trigger" / id =>
      callTheHello(id).flatMap(_ => Ok("Remote call triggered"))
  }


  private def callTheHello(id: String) =
    for {
      _ <- Logger[F].info(s"triggering call, $id")
      _ <- RemoteCaller[F].callRemote(Id(id), s"http://localhost:8080/hello/$id")
    } yield ()

}

object Http4sServer {
  import org.http4s.implicits._

  def instance[F[_]: LiftIO: Functor: ConcurrentEffect: Timer](server: Http4sServer[F])(implicit ec: ExecutionContext): HttpServer[F, Server[F]] =
    new HttpServer[F, Server[F]] {
      override def build: Resource[F, Server[F]] =
        BlazeServerBuilder[F].bindHttp(8080, "localhost")
          .withHttpApp(Router("/" -> KamonSupport(server.service)).orNotFound)
          .resource
    }
}
