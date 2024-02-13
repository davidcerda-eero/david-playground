package eero.davidplayground.csv

import java.io.{BufferedWriter, File, FileWriter}

class BasicWriter(
  writeFileName: String,
  workingDirectory: String = "results/"
) {

  private val writeFilePath = {
    val default =
      "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/data-and-results"
    if (workingDirectory.startsWith("/")) PathHelper.standardizePath(workingDirectory, writeFileName)
    else PathHelper.standardizePath(default, workingDirectory, writeFileName)
  }

  private val bufferWriter = createWrite(writeFilePath)

  def writeCSV(csv: CSVRowProcessor): Unit = {
    if (!csv.isEmpty) {
      writeRow(csv.head.getHeaderString())
      csv.rows.map(writeRow(_))
    }

  }
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
