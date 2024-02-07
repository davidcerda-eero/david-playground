package eero.davidplayground.stripe

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.stripe.model.{Invoice, Subscription}
import com.stripe.Stripe
import eero.davidplayground.csv.BasicWriter
import java.io.FileInputStream
import play.api.libs.json.{JsArray, Json}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, MINUTES}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success}

object DLQSubscriptionChecker {
  def main(args: Array[String]): Unit = {
    Stripe.apiKey = readLine("Input live key:\n")
    val jsonFile = new FileInputStream(
      "/Users/cerdadav/eero/cloud_playground/modules/davidplayground/test/eero/davidplayground/CSV_Files/data/dlq/dlq_events.json"
    )
    val writer = new BasicWriter("StripeDlqSubscriptionInfo.csv", "dlq/")
    writer.writeRow(s"subscription_id,customer_id,status,plan,tier,amount")
    val objectIds = (Json.parse(jsonFile) \ "events").as[JsArray].value.map(ev => (ev \ "object_id").as[String])
    val (subIds, invIds) = objectIds.partition(_.startsWith("sub"))
    val allSubIds = subIds ++ invIds.map(Invoice.retrieve(_).getSubscription)
    val system = ActorSystem.create()
    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
    val allSubs = Future.sequence(allSubIds.toSet.map { subId: String => Future(Subscription.retrieve(subId)) })
    Await.ready(allSubs, Duration.apply(120, MINUTES)).onComplete {
      case Success(subList) =>
        subList.map(sub =>
          writer.writeRow(
            s"${sub.getId},${sub.getCustomer},${sub.getPlan.getId},${sub.getPlan.getMetadata.get("tier")},${sub.getPlan.getAmount}"
          )
        )
        writer.closeFile()
        println(("done"))
      case Failure(exception) => println(exception.getMessage)
    }

  }
}
