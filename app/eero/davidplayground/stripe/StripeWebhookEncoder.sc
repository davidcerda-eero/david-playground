//import com.google.gson.JsonParser
//import com.stripe.model.{Event, EventData}
//import eero.common.stripe.StripeWebhookEventHelper
//import eero.premiumsubscriptionsapi.data.StripeWebhookEvent
//import play.api.libs.json.{JsValue, Json}
//
//object worksheet {
//  object helper {
//    val webhookPayload: JsValue = Json.parse(
//      """
//        |{
//        |  "id": "evt_1MHBQKBlqR9vzFLWtXDhDjMj",
//        |  "object": "event",
//        |  "api_version": "2015-07-13",
//        |  "created": 1671564264,
//        |  "data": {
//        |    "object": {
//        |      "id": "card_1M2LtRBlqR9vzFLWOlRmHT65",
//        |      "object": "card",
//        |      "address_city": null,
//        |      "address_country": null,
//        |      "address_line1": null,
//        |      "address_line1_check": null,
//        |      "address_line2": null,
//        |      "address_state": null,
//        |      "address_zip": null,
//        |      "address_zip_check": null,
//        |      "brand": "Visa",
//        |      "country": "US",
//        |      "customer": "cus_MjezPeeViNe5Nm",
//        |      "cvc_check": "pass",
//        |      "dynamic_last4": null,
//        |      "exp_month": 10,
//        |      "exp_year": 2024,
//        |      "fingerprint": "5V04ZgUsnUVCtjiV",
//        |      "funding": "credit",
//        |      "last4": "4242",
//        |      "metadata": {},
//        |      "name": null,
//        |      "tokenization_method": null
//        |    }
//        |  },
//        |  "livemode": false,
//        |  "pending_webhooks": 0,
//        |  "request": "req_MDZi0KPRrjA5oY",
//        |  "type": "customer.source.deleted"
//        |}
//""".stripMargin
//    )
//  }
//}
//
//def eventFromJson(json: JsValue): Event = {
//  val e = new Event
//  e.setApiVersion("2019-03-14")
//  e.setId((json \ "id").as[String])
//  e.setType((json \ "type").as[String])
//  e.setLivemode((json \ "livemode").as[Boolean])
//  e.setObject(Json.prettyPrint((json \ "data" \ "object").as[JsValue]))
//  e.setData {
//    val d = new EventData
//    d.setObject(JsonParser.parseString(Json.prettyPrint((json \ "data" \ "object").as[JsValue])).getAsJsonObject)
//    d
//  }
//  e
//}
//
//import worksheet.helper
//
//val webhookEvent = StripeWebhookEventHelper.eventToStripeWebhookEvent(eventFromJson(helper.webhookPayload))
//val base64Event = StripeWebhookEvent.format.toBase64String(webhookEvent)
//base64Event.getBytes.size
