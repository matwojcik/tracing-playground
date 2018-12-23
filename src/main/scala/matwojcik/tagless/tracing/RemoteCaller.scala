package matwojcik.tagless.tracing
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.tagless.finalAlg
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContext

@finalAlg
trait RemoteCaller[F[_]] {
  def callRemote(id: Id, url:String): F[Unit]
}

object RemoteCaller {

  def akkaHttp[F[_]: DeferFuture: Sync: Logger](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    materializer: ActorMaterializer
  ): RemoteCaller[F] = new RemoteCaller[F] {

    override def callRemote(id: Id, url: String): F[Unit] =
      for {
        _ <- Logger[F].info(s"About to call remote, $id")
        _ <- doCall(id, url)
        _ <- Logger[F].info(s"After the remote call, $id")
      } yield ()

    private def doCall(id: Id, url: String) =
      DeferFuture[F].defer {
        Http()
          .singleRequest(HttpRequest(uri = url))
          .map(response => response.discardEntityBytes())
      }
  }
}
