package eero.davidplayground.stripe

import eero.common.stripe.data.StripeSubscription
import eero.data.premium.UserSubscriptionStatus

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.stripe.model.{Customer, Subscription}
import com.stripe.Stripe
import eero.davidplayground.csv.CSVProcessor
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.io.StdIn.readLine
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.{Failure, Success}

object StripeSubscriptionGranter {
  def main(args: Array[String]) = {
    val liveKey = readLine()
    Stripe.apiKey = liveKey
    val system = ActorSystem.create()
    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")

    val csvLiaison = new CSVProcessor(
      "Isp_Customer_Plus_Res.csv",
      Some("Isp_Customer_Plus_Revoked.csv"),
      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/CSV_Files/",
      "data/",
      "result/"
    )
    val customers = csvLiaison.asSelectColumns("customer_id", "plan_id", "subscription_id")

    csvLiaison.writeRow("customer_id,plan_id,result")

    def runFutureWork(): Unit = {
      def doFutureStuff[T](fit: Iterable[Future[T]]): Unit = {
        val futureWork = for {
          res <- Future.sequence(fit)
        } yield res

        Await.ready(futureWork, Duration.Inf).onComplete {
          case Success(value) =>
            csvLiaison.closeFile()
          case Failure(exception) => throw exception
        }
      }

      doFutureStuff {
        customers.map(_.split(",")).collect {
          case Array(customerId, plan, subscriptionId) =>
            Future {
              var result = "still_subbed"
              val customer = Option(Customer.retrieve(customerId))
              if (customer.isDefined) {
                val subsOpt = customer.flatMap(cus => Option(cus.getSubscriptions))
                val activeSubOpt = subsOpt.flatMap(_.autoPagingIterable().asScala.map(StripeSubscription(_)).find(
                  _.status == UserSubscriptionStatus.Active
                ))

                if (activeSubOpt.nonEmpty) {
                  val subscription =
                    Option(Subscription.retrieve(subscriptionId)).filter(sub => activeSubOpt.exists(_.id == sub.getId))
                  if (subscription.nonEmpty) {

                    val sub = subscription.get
                    val planId = sub.getPlan.getId
                    println(s"here with plan: ${planId}")
                    if (planId == "price_1JDCSJBlqR9vzFLWwzGOqv9I") {

//                      sub.cancel()
                      result = "revoked"
                    }
                  }
                }
              }
              println(result)
              (result, plan)
            }.map { case (res, plan) =>
              csvLiaison.writeRow(s"$customerId, $plan, $res")
            }
        }
      }
    }
    runFutureWork()
    println("DONE")
  }
}
