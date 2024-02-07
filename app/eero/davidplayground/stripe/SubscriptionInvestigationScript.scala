//package eero.davidplayground.stripe
//
//import eero.davidplayground.csv.CSVProcessor
////import com.stripe.model.{Card, Customer, Source, Subscription}
////import com.stripe.Stripe
//
//
//object SubscriptionInvestigationScript {
//
//  def main(args: Array[String]): Unit = {
////    Stripe.apiKey = readLine("Input live key:\n")
////    val system = ActorSystem.create()
////    val t0 = Instant.now().toEpochMilli / 1000
////      implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
//    val csvLiaison = new CSVProcessor(
//      dataFileName = "migration_plans_cancel_after_2_weeks.csv",
//      writeFileName = Some("migration_plans_cancel_after_fortnite_results.csv"),
//      data = "data/stripe_migration/",
//      result = "result/stripe_migration/"
//    )
//    val subscriptions = csvLiaison.getColumn("subscription_id")
//    val randomlySelectedNumbers = Seq(141, 362, 245, 116, 573, 602, 594, 261, 478, 598)
////    val cardIdPattern = "card_*".r
//    val randomSubscriptionIds = randomlySelectedNumbers.map(subscriptions(_))
//    println(randomSubscriptionIds)
////    def scriptFunction(subscriptionId: String): Unit = {
////      val subscription = Subscription.retrieve(subscriptionId)
//////      val customer = Customer.retrieve(subscription.getCustomer)
//////      val subscriptionSource = Option(subscription.getDefaultSource).map{
//////        case cardIdPattern(_*) => Option(Source.retrieve(_))
//////        case _ => Option.empty
//////      }
//////      val customerSource = Option(customer.getDefaultSource).map{id =>
//////        case cardIdPattern(id*) => Option(Source.retrieve(_))
//////        case _ => Option.empty
//////      }
//////      val card = source.flatMap(s => Option(s.getCard))
//////      println(card.map(_.getExpYear))
//////      println(card.map(_.getExpMonth))
//////    }
//
////    subscriptions.map(scriptFunction)
//  }
//
//}
