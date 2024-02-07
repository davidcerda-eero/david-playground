package eero.davidplayground.services.callers.sqs

import awscala.sqs.{Queue, SQSClient, SQSClientWithQueue}
import com.stripe.Stripe
import com.stripe.model.{Customer, Event, Invoice, Source, Subscription}
import eero.davidplayground.csv.BasicWriter
import eero.premiumsubscriptionsapi.data.StripeWebhookEvent
import play.api.libs.json.{JsObject, Json}

import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.jdk.CollectionConverters.MapHasAsScala

object GetStripeDLQMessages {
  def main(args: Array[String]) = {
    val liveKey = readLine("Input live key:\n")
    Stripe.apiKey = liveKey
    val stripeDLQUrl = "https://sqs.us-west-2.amazonaws.com/880918510484/stripe-webhooks-event-handler-dlq-prod"

    val sqsClient = new SQSClient
    val sqsClientWithQueue = new SQSClientWithQueue(sqsClient, Queue(stripeDLQUrl))
    val eventProtoWriter = new BasicWriter("StripeEventDLQEventsProto.csv")
    eventProtoWriter.writeRow(s"event_type,event_id,event_proto")
    val eventWriter = new BasicWriter("StripeEventDLQEvents.json")
    var json = "{\n\"events\":\n["

    @tailrec
    def pollMessages(): Unit = {
      val messages = sqsClientWithQueue.receiveMessage()
      if (messages.isEmpty) {} else {
        messages.map { mes =>
          processEvent(mes.body)
        }
          sqsClientWithQueue.deleteMessages(messages: _*)
        pollMessages()
      }
    }
    def processEvent(event: String): Unit = {
      val deserializedEventOpt = StripeWebhookEvent.format.fromBase64String(event).toOption
      deserializedEventOpt match {
        case Some(ev) =>
          json = json + Json.prettyPrint(createEventJson(ev)) + ",\n"
          eventProtoWriter.writeRow(s"${ev.eventType},${ev.id},$event")
        case None => eventProtoWriter.writeRow(s"not_deserialized,not_deserialized,$event")
      }

      def createEventJson(event: StripeWebhookEvent): JsObject = {
        val eventId = event.id
        val stripeEvent = Event.retrieve(eventId)
        val eventType = event.eventType
        val previousAttributes = {
          Option(stripeEvent.getData.getPreviousAttributes).map(_.asScala).getOrElse(Map.empty[String, AnyRef])
        }
        val prevJson = previousAttributes.map { case (key, value) => key -> value.toString }
        val stripeObjectId = stripeEvent.getDataObjectDeserializer.deserializeUnsafe() match {
          case sub: Subscription => sub.getId
          case source: Source => source.getId
          case inv: Invoice => inv.getId
          case cus: Customer => cus.getId
          case _ => ""
        }
        Json.obj(
          "event_id" -> eventId,
          "event_type" -> eventType,
          "object_id" -> stripeObjectId,
          "previous_attributes" -> Json.toJson(prevJson)
        )
      }
    }
    pollMessages()
    eventWriter.writeRow(json)
    eventWriter.writeRow("]\n}")
    eventWriter.closeFile()
    eventProtoWriter.closeFile()
  }
}
