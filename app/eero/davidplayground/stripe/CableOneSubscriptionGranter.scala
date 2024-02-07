package eero.davidplayground.stripe

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.stripe.model.Subscription
import com.stripe.Stripe
import eero.davidplayground.csv.CSVProcessor

import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, MINUTES}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success}

object CableOneSubscriptionGranter {
  def main(args: Array[String]): Unit = {
    Stripe.apiKey = readLine("Input live key:\n")

    val system = ActorSystem.create()
    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
    val subscriptionsToGrantPlus = new CSVProcessor(
      "subscriptionsToGrantPlus_4.csv",
      Some("subscriptionsToGrantPlus_4.txt"),
      "inc-620-cable-one-subs"
    )
    val smallTrial: java.lang.Long = Instant.now().getEpochSecond + 300

    val subsToUpgrade = subscriptionsToGrantPlus.getColumn("stripe_subscription_id").toSeq
    val futureWork = subsToUpgrade.map { subId =>
      val subscription = StripeHelper.makeStripeCall(Subscription.retrieve(subId), true)
      subscription.flatMap(sub =>
        StripeHelper.makeStripeCall(StripeHelper.updatePlan(sub, "S020201", Option(smallTrial)), true)
      )
    }
    val subsFuture = Future.sequence(futureWork)

    Await.ready(subsFuture, Duration.apply(120, MINUTES)).onComplete {
      case Success(value) =>
        value.map { sub =>
          val subId = sub.getId
          val plan = sub.getPlan.getId
          val customer = sub.getCustomer
          subscriptionsToGrantPlus.writeRow(
            s"$subId,$plan,$customer"
          )
        }
        subscriptionsToGrantPlus.closeFile()
        println("Done")
      case Failure(exception) => throw exception
    }
  }
}
