package matwojcik.tagless.tracing

import kamon.Kamon

import scala.concurrent.ExecutionContext

class ExecutionContextAware(underlying: ExecutionContext) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = underlying.execute(new RunnableContextAware(runnable))
  override def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
}

class RunnableContextAware(underlying: Runnable) extends Runnable {
  private val traceContext = Kamon.currentContext

  override def run(): Unit =
    Kamon.withContext(traceContext) {
      underlying.run()
    }
}
