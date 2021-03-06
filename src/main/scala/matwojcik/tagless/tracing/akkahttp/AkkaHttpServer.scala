package matwojcik.tagless.tracing.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import cats.Functor
import cats.effect.{IO, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import matwojcik.tagless.tracing.DeferFuture
import matwojcik.tagless.tracing.HttpServer
import matwojcik.tagless.tracing.Id
import matwojcik.tagless.tracing.RemoteCaller
import matwojcik.tagless.tracing.Something

import scala.concurrent.ExecutionContext

class AkkaHttpServer(
  something: Something[IO],
  remoteCaller: RemoteCaller[IO]
)(implicit executionContext: ExecutionContext,
  system: ActorSystem,
  materializer: Materializer,
  logger: Logger[IO]) {

  val route =
    pathPrefix("hello") {
      path(Segment) { idString: String =>
        val id = Id(idString)
        onComplete(doTheStuff(id)) { _ => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      }
    } ~
      pathPrefix("trigger") {
        path(Segment) { id: String =>
          onComplete(callTheHello(id).unsafeToFuture()) { _ =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
          }
        }
      }

  private def callTheHello(id: String) =
    for {
      _ <- Logger[IO].info(s"triggering call, $id")
      _ <- remoteCaller.callRemote(Id(id), s"http://localhost:8080/hello/$id")
    } yield ()

  private def doTheStuff(id: Id) =
    (for {
      _ <- Logger[IO].info(s"Hello action triggered, $id")
      _ <- something.functionWithALotOfLogging(id)
    } yield ()).unsafeToFuture()
}

object AkkaHttpServer {

  def instance[F[_]: DeferFuture: Functor](server: AkkaHttpServer)(implicit system: ActorSystem, materializer: Materializer): HttpServer[F, Http.ServerBinding] =
    new HttpServer[F, Http.ServerBinding] {
      override def build: Resource[F, Http.ServerBinding] = Resource.make(DeferFuture[F].defer {
        Http().bindAndHandle(server.route, "localhost", 8080)
      })(binding => DeferFuture[F].defer(binding.unbind()).as(()))
    }

}
