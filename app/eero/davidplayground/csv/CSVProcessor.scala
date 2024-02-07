package eero.davidplayground.csv

class CSVProcessor(
  val dataFileName: String,
  val writeFileName: Option[String],
  override val workingDirectory: String =
    "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/eero/davidplayground/CSV_Files",
  override val data: String = "data/",
  override val result: String = "result/"
) extends BasicCSVProcessor
