import eero.data.id.UserId
import eero.premiumsubscriptionsapi.data.InAppPaymentsWebhookNotifications.NotificationSource

trait WebhookView {
  val subscriptionId: String
  val userId: UserId
  val source: NotificationSource
  // ..
  // ..
  // ..
}

object WebhookProtobuf {
  def read
  def write
}

case class GoogleWebhookPayload(
  override val subscriptionId: String,
  override val userId: UserId,
  override val source: NotificationSource,
  val extraStuff: String
) extends WebhookView

object GoogleWebhookProtobuf {
  def read
  def write
}

def webhookProcessing(webhookPayload: WebhookView): UserId = {
  webhookPayload.userId
}

def logging(webhookView: WebhookView): Unit = {
  webhookView match {
    case goog : GoogleWebhookPayload => println(s"I'm am a googler, ${goog.source}")
  }
}
