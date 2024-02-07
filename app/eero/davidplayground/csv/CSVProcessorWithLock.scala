package eero.davidplayground.csv

import java.util.concurrent.locks.ReentrantLock

class CSVProcessorWithLock(
  val dataFileName: String,
  val writeFileName: Option[String],
  override val workingDirectory: String,
  override val data: String = "data/",
  override val result: String = "result/"
) extends BasicCSVProcessor {

  val lock = new ReentrantLock()

  override def writeRow(string: String): Unit = {
    lock.lock()
    try {
      Thread.sleep(10)
      super.writeRow(string)
    } finally {
      lock.unlock()
    }
  }
}
