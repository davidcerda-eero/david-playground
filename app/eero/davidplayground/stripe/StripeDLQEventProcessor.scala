package eero.davidplayground.stripe

import eero.common.stripe.StripeWebhookEventHelper
import eero.premiumsubscriptionsapi.protobuf.PStripeWebhookEvent.StripeData
import com.stripe.model.{Customer, Invoice, Subscription}
import com.stripe.Stripe
import eero.davidplayground.csv.CSVProcessor

import scala.io.StdIn.readLine

object StripeDLQEventProcessor {
  def main(args: Array[String]) = {
    val liveKey = readLine("Input live key:\n")
    Stripe.apiKey = liveKey

    val csvLiason = new CSVProcessor(
      "dlq_relevant_events.csv",
      Some("customer_and_sub_id.csv"),
      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/CSV_Files/"
    )
    val events = csvLiason.getColumn("event_id").map(ev =>
      StripeWebhookEventHelper.eventToStripeWebhookEvent(StripeHelper.getRawEvent(ev))
    )

    val invoices = events.flatMap { ev =>
      ev.data match {
        case StripeData.Invoice(value) => Some(Invoice.retrieve(value.getId))
        case _ => None
      }
    }

    val customersAndSubs = invoices.map { inv =>
      (Customer.retrieve(inv.getCustomer).getId, Subscription.retrieve(inv.getSubscription).getId)
    }
    csvLiason.writeRow("customer_id,subscription_id")
    customersAndSubs.map { case (cus, sub) =>
      csvLiason.writeRow(s"$cus,$sub")
    }
    csvLiason.closeFile()
  }

}
