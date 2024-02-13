package eero.davidplayground.csv

class CSVProcessor(
  val dataFileName: String,
  val writeFileName: Option[String],
  override val workingDirectory: String =
    "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/data-and-results",
  override val data: String = "data/",
  override val result: String = "results/"
) extends BasicCSVProcessor
