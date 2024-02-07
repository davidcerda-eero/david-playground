package eero.davidplayground.csv

import java.io.{BufferedWriter, File, FileWriter}

class BasicWriter(
  writeFileName: String,
  workingDirectory: String = "result/"
) {

  private val writeFilePath = {
    val default =
      "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/eero/davidplayground/CSV_Files"
    if (workingDirectory.startsWith("/")) PathHelper.standardizePath(workingDirectory, writeFileName)
    else PathHelper.standardizePath(default, workingDirectory, writeFileName)
  }

  private val bufferWriter = createWrite(writeFilePath)

  def writeRow(row: Iterable[String]): Unit = {
    bufferWriter.write(row.mkString(",") + "\n")
  }
  def writeRow(row: String): Unit = {
    bufferWriter.write(row + "\n")
  }

  def write(string: String): Unit = {
    bufferWriter.write(string)
  }

  private def createWrite(filename: String): BufferedWriter = {
    println(filename)
    val file = new File(filename)
    file.createNewFile()
    new BufferedWriter(new FileWriter(file))
  }

  def closeFile(): Unit = {
    bufferWriter.flush()
    bufferWriter.close()
  }
}
