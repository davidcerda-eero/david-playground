package eero.davidplayground

import eero.davidplayground.csv.CSVProcessor

object CSVCompare {
  def main(args: Array[String]) = {
    val CableOnePartnerIdsWithSerials= new CSVProcessor(
      "Invesitgation_CableOnePartnerIdsWithSerial.csv",
      None,
      "inc-620-cable-one-subs"
    )

    val CableOneSerialsWithNetworkId = new CSVProcessor(
      "Investigation_CableOneCASerialsWNetworks.csv",
      None,
      "inc-620-cable-one-subs"
    )

//    CableOnePartnerIdsWithSerials.JoinOnMatchingColumns(CableOneSerialsWithNetworkId, true, "serial_number" -> "serial_number").foreach { r =>
//      println(r.mkString(","))
  }
  }
