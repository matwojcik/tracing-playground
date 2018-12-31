package matwojcik.tagless.tracing
import cats.effect.Resource
import cats.tagless.finalAlg

@finalAlg
trait HttpServer[F[_], T] {
  def build: Resource[F, T]
}
