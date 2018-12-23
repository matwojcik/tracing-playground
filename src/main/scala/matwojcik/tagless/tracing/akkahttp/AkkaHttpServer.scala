package matwojcik.tagless.tracing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import cats.effect.{IO, LiftIO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContext

class HttpServer[F[_]: LiftIO: Something: RemoteCaller: Logger](implicit executionContext: ExecutionContext,
  system: ActorSystem,
  materializer: Materializer) {

  val route =
    pathPrefix("hello") {
      path(Segment) { idString: String =>
        val id = Id(idString)
        onComplete(doTheStuff(id)) {
          _ => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
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
      _ <- Logger[F].info(s"Hello action triggered, $id")
      _ <- Something[F].functionWithALotOfLogging(id)
    } yield ()).unsafeToFuture()
}
