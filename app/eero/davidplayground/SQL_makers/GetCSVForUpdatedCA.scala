package eero.davidplayground.SQL_makers

import eero.davidplayground.csv.{BasicWriter, CSVProcessor}

object GetCSVForUpdatedCA {
  def main(args: Array[String]): Unit = {
    val cableOneCAMissingUserId = new CSVProcessor(
      "Case_B.1_-_Cable_One_CA_missing_user_id_2024_02_02.csv",
      Some("UpdateCableOne.sql"),
      "inc-620-cable-one-subs"
    )
    val (unique, _) = cableOneCAMissingUserId.separateDuplicates("user_id")
    val writer = new BasicWriter("Updated_CA_Cable_One.csv", "result/inc-620-cable-one-subs")
    unique.processRows(writer.writeRow(_))
    writer.closeFile()
  }
}
