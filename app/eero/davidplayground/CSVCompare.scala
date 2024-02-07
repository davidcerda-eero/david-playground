package eero.davidplayground

import eero.davidplayground.csv.CSVProcessor

object CSVCompare {
  def main(args: Array[String]) = {
    val CableOnePartnerIdsWithSerials= new CSVProcessor(
      "Investigation_Cable_Partner_Ids.csv",
      None,
      "inc-620-cable-one-subs"
    )

    val CableOneSerialsWithNetworkId = new CSVProcessor(
      "Investigation-serials-with-network.csv",
      None,
      "inc-620-cable-one-subs"
    )

    for {
      row <- CableOneSerialsWithNetworkId
      string <- row
    } yield {row }

//    CableOnePartnerIdsWithSerials.JoinOnMatchingColumns(CableOneSerialsWithNetworkId, true, "serial_number" -> "serial_number").foreach { r =>
//      println(r.mkString(","))
  }
  }
