package eero.davidplayground.stripe

import eero.common.stripe.{StripeSubscriptionEvent, StripeWebhookEventHelper}
import eero.data.premium.StripeSubscriptionStatus.canceled
import com.stripe.model.{Event, StripeCollection}
import com.stripe.Stripe

import java.time.ZonedDateTime
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.io.StdIn.readLine
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.{Failure, Success}

object StripeEventGetter {
  def main(args: Array[String]): Unit = {
    val liveKey = readLine("Input live key:\n")
    Stripe.apiKey = liveKey
    implicit val ec = ExecutionContext.global

    def getEventsUntilDone(eventTypes: Seq[String], start: Long): Future[Seq[Event]] = {
      def getEvents(eventsFuture: Future[StripeCollection[Event]], previousData: Seq[Event]): Future[Seq[Event]] = {
        (for {
          events <- eventsFuture
          webhookEvents = events.autoPagingIterable().asScala
          hasMore = events.getHasMore
        } yield {
          if (!hasMore) {
            Future.successful(previousData ++ webhookEvents)
          } else {
            val nextEvents = StripeHelper.makeStripeCollectionCall(
              StripeHelper.getEvents(eventTypes, start, webhookEvents.lastOption.map(_.getId)),
              true
            )
            getEvents(nextEvents, previousData ++ webhookEvents)
          }
        }).flatten
      }

      val initialEvents = StripeHelper.makeStripeCollectionCall(StripeHelper.getEvents(eventTypes, start, None), true)
      getEvents(initialEvents, Seq.empty)
    }
    val eventTypes = Seq(
      "customer.subscription.updated"
    )
    val start = ZonedDateTime.parse("2023-10-20T19:53:27-00:00[UTC]").toEpochSecond
    Await.ready(getEventsUntilDone(eventTypes, start), Duration.Inf).onComplete {
      case Success(events) =>
        events.map { ev =>
          val status =
            StripeSubscriptionEvent(StripeWebhookEventHelper.eventToStripeWebhookEvent(ev)).data.optStripeStatus
          if (status.contains(canceled)) println(s"Event: ${ev.getId}, Status: $status")
        }
      case Failure(exception) => exception
    }
  }
}
