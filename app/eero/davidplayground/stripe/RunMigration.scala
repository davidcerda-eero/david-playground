package eero.davidplayground.stripe

object RunMigration {
  def main(args: Array[String]): Unit = {
//    Stripe.apiKey = "live_key"
//    val scheduler = new StripeScheduler("live")
//
//    val data = Seq("cus_JzHD6eW7T9Ll11", "cus_GSpMc26Cqws2Bs")
//
//    data.map { cusId =>
//      val (cid, status, time) = {
//        val customer = Customer.retrieve(cusId)
//        val (_, sch) = scheduler.getSubscriptionWithSchedule(customer)
//        sch.map(StripeHelper.getRawSubscriptionSchedule(_).release())
//        scheduler.migrateCustomer(
//          cusId,
//          Instant.now().plus(Duration.ofSeconds(30)).getEpochSecond,
//          Instant.now().getEpochSecond
//        )
//      }
//      println(s"$cid,$status,${Instant.ofEpochSecond(time).atZone(ZoneId.of("UTC-8"))}")
//    }
  }
}
