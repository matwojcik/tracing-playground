package matwojcik.tagless.tracing
import cats.tagless.finalAlg

@finalAlg
trait HttpServer[F[_]] {
  def build: F[Unit]
}
