package eero.davidplayground.stripe

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.stripe.Stripe
import com.stripe.exception.RateLimitException
import eero.common.stripe.{StripeSubscriptionEvent, StripeWebhookEventHelper}
import eero.davidplayground.csv.CSVProcessor

import scala.concurrent.duration.{Duration, MINUTES}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object StripeEventProcessor {
  def main(args: Array[String]): Unit = {
    Stripe.apiKey = "live_key"
    //    val targetPlans = Seq(
    //      "price_1M2hYJBlqR9vzFLWCdoXQqEf",
    //      "price_1M2hVFBlqR9vzFLWzuGdpdAi",
    //      "price_1M2hTGBlqR9vzFLWn6VTfeoT",
    //      "price_1M4BqTBlqR9vzFLWFuBf1oQq"
    //    )
    val system = ActorSystem.create()
    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
//    implicit val ec = ExecutionContext.global

    val migrationEvents = new CSVProcessor(
      "migration_events2.csv",
      None,
      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/events/",
      "data/",
      "result/"
    )

    val subscriptionsToFix = new CSVProcessor(
      "Subscriptions_To_Fix.csv",
      Some("subscriptions_with_needed_info.csv"),
      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/events/",
      "data/",
      "result/"
    )

    val sharedSubs = migrationEvents.compareElements(subscriptionsToFix).toList
    val eventsForSubsFuture = Future.sequence(sharedSubs.tail.collect {
      case Array(ev, _, _, _) =>
        Future {
          Thread.sleep(1000)
          StripeHelper.getRawEvent(ev)
        }.recoverWith {
          case _: RateLimitException =>
            Future {
              Thread.sleep(1000)
              StripeHelper.getRawEvent(ev)
            }
        }
    })

    subscriptionsToFix.writeRow("event_id,subscription_id,customer_id,plan_id,event_type")
    val eventsWithCreated = eventsForSubsFuture.map(_.map { event =>
      (StripeSubscriptionEvent(StripeWebhookEventHelper.eventToStripeWebhookEvent(event)), event.getCreated)
    }.groupMapReduce(_._1.data.id)(identity)((a, b) => Seq(a, b).maxBy(_._2)).values.map(_._1))

    Await.ready(eventsWithCreated, Duration.apply(120, MINUTES)).onComplete {
      case Success(value) =>
        value.map { event =>
          subscriptionsToFix
            .writeRow(
              s"${event.id},${event.data.id},${event.data.optCustomerId},${event.data.plan.id},${event.eventType}"
            )
          println(event)
        }
        subscriptionsToFix.closeFile()
        println("Done")
      case Failure(exception) => throw exception
    }

  }
}
