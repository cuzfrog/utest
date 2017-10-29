package utest
package framework


import scala.util.{Failure, Success, Try}
import scala.language.experimental.macros
import utest.PlatformShims

import scala.concurrent.Future

case class TestPath(value: Seq[String])
object TestPath{
  @reflect.internal.annotations.compileTimeOnly(
    "TestPath is only available within a uTest suite, and not outside."
  )
  implicit def synthetic: TestPath = ???
}


/**
  * The executable portion of a tree of tests. Each node contains an
  * executable, which when run either returns a Left(result) or a
  * Right(sequence) of child nodes which you can execute.
  */
class TestCallTree(inner: => Either[(Try[Any], () => Unit), IndexedSeq[TestCallTree]]){
  /**
   * Runs the test in this [[TestCallTree]] at the specified `path`. Called
   * by the [[TestTreeSeq.run]] method and usually not called manually.
   */
  def run(path: List[Int])(implicit ec: scala.concurrent.ExecutionContext): Any = {
    path match {
      case head :: tail =>
        val Right(children) = StackMarker.dropOutside(inner)
        children(head).run(tail)
      case Nil =>
        val Left((resTry, hook)) = inner
        val res = resTry match{
          case Success(_res) => _res
          case Failure(e) => hook(); throw e
        }
        val resFuture = res match {
          case x: Future[_] => x
          case notFuture => Future.successful(notFuture)
        }
        resFuture.map { r => hook(); r }

    }
  }
}

/**
 * A single test's result after execution. Any exception thrown or value
 * returned by the test is stored in `value`. The value returned can be used
 * in another test, which adds a dependency between them.
 */
case class Result(name: String,
                  value: Try[Any],
                  milliDuration: Long)
