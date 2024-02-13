package eero.davidplayground.csv

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

trait BasicCSVProcessor extends CSVRowProcessor {
  val dataFileName: String
  val workingDirectory: String = "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/data-and-results"

  val data: String = "data/"
  val result: String = "results/"

  private val dataFullPath = {
    val default = "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/data-and-results"
    val elements = if (workingDirectory.startsWith("/")) {
      Seq(workingDirectory, data, dataFileName)
    } else {
      Seq(default, data, workingDirectory, dataFileName)
    }
    PathHelper.standardizePath(elements: _*)
  }

  override private[csv] val (headers: Map[String, Int], rows: Vector[CSVRow]) = {
    val bufferedSource = Source.fromFile(dataFullPath)
    val csvLines = ArrayBuffer[CSVRow]()
    val lines = bufferedSource.getLines()
    val headers = lines.next().split(",").map(_.trim).zipWithIndex.toMap
    for (line <- lines) {
      val row = line.split(",", -1).map(_.trim)

      csvLines += new CSVRow(headers, row.toVector)
    }
    bufferedSource.close()
    (headers, csvLines.toVector)
  }

  val writeFileName: Option[String]
  private val optCsvWriter: Option[BasicWriter] =
    writeFileName.map {
      case file if workingDirectory.startsWith("/") => new BasicWriter(file, workingDirectory + "/" + result)
      case file => new BasicWriter(file, result + workingDirectory)
    }

  def write(statement: String): Unit = {
    optCsvWriter.map(_.write(statement))
  }

  def writeRow(row: String): Unit = {
    optCsvWriter.map(_.writeRow(row))
  }

  def writeThisCSV(): Unit = {
    optCsvWriter.map(_.writeCSV(this))
  }

  def closeFile(): Unit = {
    optCsvWriter.map(_.closeFile())
  }
}
