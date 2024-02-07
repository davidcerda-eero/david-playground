package eero.davidplayground

import eero.davidplayground.csv.CSVProcessor

object ProcessResults {
  def main(args: Array[String]): Unit = {
    val subscriptionsToFix = new CSVProcessor(
      "subscriptions_with_needed_info.csv",
      Some("events_to_rerun_with_sub.csv"),
      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/events/",
      "data/",
      "result/"
    )
    subscriptionsToFix.writeRow("event_id,subscription_id")
    subscriptionsToFix.asSelectColumns("event_id", "subscription_id").foreach(subscriptionsToFix.writeRow)
    subscriptionsToFix.closeFile()
  }
}
