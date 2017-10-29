package test.utest

import java.util.concurrent.atomic.AtomicInteger
import utest.framework.ExecutionContext.RunNow
import utest._

object AutoCloseableTest extends TestSuite {

  val tests: Tests = Tests {
    'testAutoClose {
      val innerTests = Tests {
        val resource = utestAutoClose(Resource.create)
        //val resource = Resource.create
        var x = 0
        'test1 - {
          'inner1 - {
            resource.useMe()
          }
          'inner2 - {
            throw new java.lang.AssertionError("error-happened1")
            resource.useMe()
          }
        }
        'test2 - {
          resource.useMe()
        }
        'failure - {
          throw new java.lang.AssertionError("error-happened2")
          resource.useMe()
        }
      }

      TestRunner.runAsync(innerTests, executor = this).map { results =>
        val leafResults = results.leaves.toSeq
        assert(leafResults(0).value.isSuccess) //inner1
        assert(leafResults(1).value.isFailure) //inner2
        assert(leafResults(2).value.isSuccess) //test2
        assert(leafResults(3).value.isFailure) //failure
        assert(Resource.createCnt == 4)
        assert(Resource.usageCnt == 2)
        assert(Resource.closedCnt == 4)
        leafResults
      }
    }
  }

  private class Resource private(id: Int) extends AutoCloseable {
    private[this] var closed: Boolean = false
    override def close(): Unit = {
      println(s"Close resource$id")
      closed = false
      Resource.closeCounter.getAndIncrement()
    }
    def isClosed: Boolean = closed
    def useMe(): Unit = {
      println(s"Use resource$id")
      Resource.usageCounter.getAndIncrement()
    }
  }

  private object Resource {
    private[this] val creationCounter: AtomicInteger = new AtomicInteger(0)
    private val usageCounter: AtomicInteger = new AtomicInteger(0)
    private val closeCounter: AtomicInteger = new AtomicInteger(0)

    def create: Resource = {
      val id = creationCounter.getAndIncrement()
      println(s"Create resource$id")
      new Resource(id)
    }
    def createCnt: Int = creationCounter.get()
    def usageCnt: Int = usageCounter.get()
    def closedCnt: Int = closeCounter.get()
  }
}
