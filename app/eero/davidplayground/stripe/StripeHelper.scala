package eero.davidplayground.stripe

import com.google.gson.{JsonElement, JsonNull}
import com.stripe.exception.{InvalidRequestException, RateLimitException}
import com.stripe.model.{Card, Coupon, Customer, Event, EventCollection, HasId, Invoice, Price, PriceCollection, StripeCollection, Subscription, SubscriptionSchedule, Token}
import com.stripe.net.ApiResource
import com.stripe.param.SubscriptionScheduleUpdateParams.EndBehavior
import com.stripe.param.{EventListParams, InvoiceListParams, PriceListParams, SubscriptionScheduleCreateParams, SubscriptionScheduleUpdateParams, SubscriptionUpdateParams}
import eero.common.stripe.StripeWebhookEventHelper
import eero.common.stripe.data.StripePhase.toPhaseUpdateParam
import eero.common.stripe.data.{StripeCoupon, StripeCustomer, StripeDuration, StripePhase}
import eero.common.time.InstantRange
import eero.core.Identity.wrapIdentity
import eero.core.concurrent.FOption
import eero.data.i18n.CountryCode
import eero.data.id.UserId
import eero.data.premium.UserSubscriptionStatus
import eero.premiumsubscriptionsapi.data.{BillingAddress, StripeWebhookEvent}

import java.lang.{Boolean => JavaBoolean}
import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object StripeHelper {
  private val taxCode = "SW053000"

  def attachPayment(customerId: String): Customer = {
    val cardToken = {
      val number: Map[String, Object] = Map("number" -> "4242424242424242")
      val expMonth: Map[String, Object] = Map("exp_month" -> Integer.valueOf(10))
      val expYear: Map[String, Object] = Map("exp_year" -> Integer.valueOf(2024))
      val cvc: Map[String, Object] = Map("cvc" -> "420")
      val card: Map[String, Object] = Map("card" -> (number ++ expMonth ++ expYear ++ cvc).asJava)
      Token.create(card.asJava)
    }

    val sourceParams: Map[String, Object] = Map("source" -> cardToken.getId)
    val customer = Customer.retrieve(customerId)
    customer.getSources.create(sourceParams.asJava)
    customer
  }

  def markInvoiceAsUncollectible(customerId: String)(implicit ec: ExecutionContext): Future[Option[Invoice]] = {
    val invoiceListParams = {
      InvoiceListParams.builder().setCustomer(customerId).setLimit(1).build()
    }
    val open = "open"
    val optLastInvoiceFuture =
      Future(Invoice.list(invoiceListParams)).map(_.autoPagingIterable().asScala.headOption)
    (for {
      lastInvoice <- FOption(optLastInvoiceFuture)
      hasOpenStatus = lastInvoice.getStatus == open
      hasAttempted = lastInvoice.getAttempted
      hasPaid = lastInvoice.getPaid
      hasAutoAdvance = lastInvoice.getAutoAdvance
      shouldMarkUncollectible = hasOpenStatus && hasAttempted && !hasPaid && !hasAutoAdvance
      markedInvoice <- if (shouldMarkUncollectible) {
        FOption({
          Future(lastInvoice.markUncollectible())
        })
      } else {
        FOption(lastInvoice)
      }
    } yield {
      markedInvoice
    }).recover {
      case _: InvalidRequestException => Option.empty[Invoice]
    }.futureOpt
  }

  def getEvent(id: String): StripeWebhookEvent = {
    StripeWebhookEventHelper.eventToStripeWebhookEvent(Event.retrieve(id))
  }

  def getRawEvent(id: String): Event = {
    Event.retrieve(id)
  }

  def getEvents(
    eventTypes: Seq[String],
    createdAfter: java.lang.Long,
    startingAfter: Option[String]
  ): EventCollection = {
    val params = {
      val eventListParams = EventListParams.builder()
      val createdGte = EventListParams.Created.builder().setGte(createdAfter).build()
      eventListParams
        .addAllType(eventTypes.asJava)
        .setLimit(100)
        .setCreated(createdGte)
      startingAfter.fold(eventListParams.build())(eventListParams.setStartingAfter(_).build())
    }
    Event.list(params)
  }

  def getPrices(
    startingAfter: Option[String]
  ): PriceCollection = {
    val params = {
      val listParams = PriceListParams.builder()
      listParams
        .setLimit(100)
      startingAfter.fold(listParams.build())(listParams.setStartingAfter(_).build())
    }
    Price.list(params)
  }

  def getRawSubscriptionSchedule(
    subscriptionScheduleId: String
  ): SubscriptionSchedule = {
    {
      SubscriptionSchedule.retrieve(subscriptionScheduleId)
    }
  }

  def subscribeAndGetCustomer(
    planId: String,
    noTrial: Boolean,
    optCoupon: Option[String],
    // This declaration is necessary to ensure that a Java Boolean is passed rather than a Scala one
    payImmediately: JavaBoolean = false,
    metadataOpt: Option[Map[String, Object]] = None
  )(customerId: String)(implicit ec: ExecutionContext): Future[StripeCustomer] = {
//    val stripeCreateParams = SubscriptionCreateParams.builder()
//    val addParams = {
//      val itemBuilder = SubscriptionCreateParams.Item.builder()
//      stripeCreateParams.setCustomer(customerId)
//      stripeCreateParams.addItem(itemBuilder.setPlan(planId).build())
//      stripeCreateParams.setTrialFromPlan(!noTrial)
//    }
    val baseParams = Map[String, Object](
      "plan" -> planId,
      "customer" -> customerId,
      "pay_immediately" -> payImmediately,
      "trial_from_plan" -> JavaBoolean.valueOf(!noTrial)
    )

    val subParams = if (noTrial) {
      baseParams ++ Map[String, Object]("trial_end" -> "now")
    } else {
      baseParams
    }

    val subParamsWithCoupon = subParams.applyOpt(optCoupon) {
      case (params, coupon) =>
        params ++ Map[String, Object]("coupon" -> coupon)
    }

    val subParamsWithMetadata = subParamsWithCoupon.applyOpt(metadataOpt) {
      case (params, metadata) =>
        params ++ Map[String, Object]("metadata" -> metadata.asJava)
    }

    val currentActivePlans = StripeCustomer(Customer.retrieve(customerId)).subscriptions
      .filter(sub => UserSubscriptionStatus.isActive(sub.status))
      .map(_.plan.id)

    if (!currentActivePlans.contains(planId)) {
      makeStripeCall(Subscription.create(subParamsWithMetadata.asJava), false)
    }

    makeStripeCall(Customer.retrieve(customerId), false).map(StripeCustomer(_))
  }

  def startSubscription(
    customerId: String,
    planId: String,
    trial: Option[java.lang.Long],
    backDate: Option[java.lang.Long]
  ): Subscription = {
    val baseParams = Map[String, Object](
      "plan" -> planId,
      "customer" -> customerId,
      "trial_from_plan" -> JavaBoolean.valueOf(false)
    )
    val noTrial = Map[String, Object]("trial_end" -> "now")
    val params = trial.fold(baseParams ++ noTrial) { trialTime =>
      baseParams ++ Map[String, Object]("trial_end" -> trialTime)
    }

    val backDateParams = params.applyOpt(backDate) {
      case (params, back) => params ++ Map[String, Object]("backdate_start_date" -> back)

    }
    Try {
      Subscription.create(backDateParams.asJava)
    } match {
      case Success(sub) => sub
      case Failure(err: InvalidRequestException) if err.getCode == "resource_missing" =>
        attachPayment(customerId)
        Subscription.create(params.asJava)
      case Failure(err) => throw err
    }
  }

  def createScheduleFromSubscription(
    subscriptionId: String,
    metadata: Map[String, String] = Map.empty[String, String]
  )(implicit ec: ExecutionContext): Future[SubscriptionSchedule] = {
    val params = SubscriptionScheduleCreateParams
      .builder()
      .setFromSubscription(subscriptionId)
      .putAllMetadata(metadata.asJava)
      .build()

    Future(SubscriptionSchedule.create(params))
  }

  def releaseSchedule(schedule: SubscriptionSchedule): SubscriptionSchedule = {
    schedule.release()
  }

  def reactivateSchedule(
    schedule: SubscriptionSchedule,
    optPlan: Option[String],
    optCoupon: Option[String],
    halfTimeOff: Long
  ): SubscriptionSchedule = {
    val phases = schedule.getPhases.asScala.toSeq.map(StripePhase.apply)
    val optLastPhaseEnd = phases.lastOption.map(_.range.end)
    val halfOffPhase = optLastPhaseEnd.map { end =>
      StripePhase(optPlan, optCoupon, None, InstantRange(end, end.plus(Duration.ofMinutes(halfTimeOff))))
    }
    val subscriptionScheduleUpdateParams = SubscriptionScheduleUpdateParams
      .builder()
      .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.RELEASE)
      .addAllPhase((phases ++ halfOffPhase).map(StripePhase.toPhaseUpdateParam(_)).asJava)
      .build()
    schedule.update(subscriptionScheduleUpdateParams)
  }

  def getOptionalRawScheduleFromSubscription(subscriptionId: String): Option[SubscriptionSchedule] = {
    val subscription = Subscription.retrieve(subscriptionId)
    subscription.getRawJsonObject.get("schedule") match {
      case _: JsonNull => None
      case schedule: JsonElement =>
        Option(SubscriptionSchedule.retrieve(schedule.getAsString))
    }
  }

  def reactivateSubscriptionOrSchedule(
    subscription: Subscription,
    optPlan: Option[String],
    optCoupon: Option[String],
    halfTimeOff: Long
  ): Subscription = {
    val subscriptionId = subscription.getId
    getOptionalRawScheduleFromSubscription(subscriptionId) match {
      case Some(schedule) if schedule.getEndBehavior == "none" =>
        reactivateSchedule(schedule, optPlan, optCoupon, halfTimeOff)
        subscription
      case None => reactivateSubscription(subscription)
      case Some(_) =>
        println("No need to reactivate subscription")
        subscription
    }
  }

  def reactivateSubscription(subscription: Subscription): Subscription = {
    val subscriptionUpdateParams = SubscriptionUpdateParams.builder()
    subscription.update(subscriptionUpdateParams.setCancelAtPeriodEnd(false).build())
  }

  def cancelScheduledSubscription(
    subscription: Subscription,
    schedule: SubscriptionSchedule
  ): SubscriptionSchedule = {
    val phases = schedule.getPhases.asScala.toSeq
    val currentPeriodEnd = subscription.getCurrentPeriodEnd
    val filteredPhases = phases.filterNot(_.getStartDate >= currentPeriodEnd).map(StripePhase.apply)
    println(filteredPhases)
    val params = SubscriptionScheduleUpdateParams
      .builder()
      .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.NONE)
      .addAllPhase(filteredPhases.map(toPhaseUpdateParam).asJava)
      .build()
    schedule.update(params)
  }

  def cancelSubscription(
    subscription: Subscription,
    atPeriodEnd: JavaBoolean = true
  )(implicit ec: ExecutionContext): Future[Subscription] = {
    val params = Map[String, Object](
      "cancel_at_period_end" -> atPeriodEnd,
      "items" -> Seq(
        Map[String, Object](
          "id" -> subscription.getItems.getData.get(0).getId,
          "price" -> subscription.getPlan.getId
        ).asJava
      ).asJava
    )

    Future(subscription.update(params.asJava))
  }

  def createAndApplyCoupon(
    customerId: String,
    discountPercent: Int,
    duration: StripeDuration.Value,
    durationInDays: Int,
    applicableProducts: Seq[String]
  )(implicit ec: ExecutionContext): Future[StripeCoupon] = {

    val couponParams = Map[String, Object](
      "percent_off" -> Integer.valueOf(discountPercent),
      "duration" -> duration.toString.toLowerCase(),
      "duration_in_months" -> Integer.valueOf(Math.round(durationInDays.toFloat / 30)),
      "max_redemptions" -> Integer.valueOf(1),
      "applies_to" -> Map[String, Object]("products" -> applicableProducts.asJava).asJava
    ).asJava

    Future {
      val coupon = Coupon.create(couponParams)
      val customerParams = Map[String, Object](
        "coupon" -> coupon.getId
      ).asJava
      val customer = Customer.retrieve(customerId)
      customer.update(customerParams)
      coupon
    }.map(StripeCoupon.apply)
  }

  def updateSubscriptionXSecondsInFuture(
    subscriptionId: String,
    planId: String,
    couponOpt: Option[String],
    prorate: Boolean = false,
    undoUpcomingCancellation: Boolean = false,
    resetAnchor: Boolean = true,
    secondsInFuture: Long
  )(implicit ec: ExecutionContext): Future[SubscriptionSchedule] = {
    for {

      subscription <- Future(Subscription.retrieve(subscriptionId))
      schedule <- createScheduleFromSubscription(subscription.getId)
      currentPhases = schedule.getPhases.asScala.toSeq
      updatedSchedule <- {
        val updateTime = Instant.now().plus(Duration.ofSeconds(secondsInFuture))
        val currentPhase = StripePhase(currentPhases.head)
        val modifiedCurrentPhase =
          currentPhase.copy(trialEnd = None, range = InstantRange(currentPhase.range.start, updateTime))
        val updatePhase = StripePhase(
          Some(planId),
          couponOpt,
          None,
          InstantRange(updateTime, updateTime.plus(Duration.ofSeconds(secondsInFuture))),
          true
        )
        val renewalBehavior = if (undoUpcomingCancellation) {
          EndBehavior.RELEASE
        } else {
          EndBehavior.NONE
        }
        val updateParams = SubscriptionScheduleUpdateParams
          .builder()
          .setEndBehavior(renewalBehavior)
          .addAllPhase(Seq(modifiedCurrentPhase, updatePhase).map(toPhaseUpdateParam(_)).asJava)
        Future(schedule.update(updateParams.build()))
      }
    } yield updatedSchedule
  }

  def updateSubscription(
    subscriptionId: String,
    planId: String,
    couponOpt: Option[String],
    prorate: Boolean = false,
    undoUpcomingCancellation: Boolean = false,
    resetAnchor: Boolean = true
  )(implicit ec: ExecutionContext): Future[Subscription] = {
    for {
      subscription <- Future(Subscription.retrieve(subscriptionId))

      updateParams = {
        val requiresPlanUpdate = subscription.getPlan != planId
        val paramBuilder = if (undoUpcomingCancellation && subscription.getCancelAtPeriodEnd) {
          SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(false)
        } else SubscriptionUpdateParams.builder()
        if (requiresPlanUpdate) {
          val inOneHour = Instant.now.plus(Duration.ofHours(1)).getEpochSecond
          val itemParam = SubscriptionUpdateParams.Item
            .builder()
            .setId(subscription.getItems.getData.get(0).getId)
            .setPlan(planId)

          val updateParams = paramBuilder
            .addItem(itemParam.build())
            .setProrate(prorate)

          val billingParams = if (resetAnchor) {
            updateParams.setTrialEnd(inOneHour)
          } else {
            updateParams
          }

          (billingParams.build().toMap.asScala ++ Map[String, Object]("coupon" -> couponOpt.getOrElse(null))).asJava
        } else {
          couponOpt.fold(paramBuilder)(paramBuilder.setCoupon).build().toMap
        }
      }
      updatedSubscription <- {
        if (updateParams.isEmpty) {
          Future.successful(subscription)
        } else {
          Future(subscription.update(updateParams))
        }
      }
    } yield {
      updatedSubscription
    }
  }

  def updatePlan(
    subscription: Subscription,
    planId: String,
    billingCycle: Option[java.lang.Long]
  ): Subscription = {
    val subscriptionItem = subscription.getItems.getData.get(0).getId
    val planParam = Map[String, Object](
      "items" -> Seq(
        Map[String, Object](
          "id" -> subscriptionItem,
          "price" -> planId
        ).asJava
      ).asJava
    )
    val params = planParam.applyOpt(billingCycle) {
      case (param, renew) => param ++ Map[String, Object]("trial_end" -> renew)
    }
    Option(subscription.getDiscount()).map(_ => subscription.deleteDiscount())
    subscription.update(params.asJava)

  }

  def updateScheduledSubscription(
    subscription: Subscription,
    schedule: SubscriptionSchedule,
    planId: String
  ): SubscriptionSchedule = {
    val releasedSchedule = schedule.release()
    val subscriptionItem = subscription.getItems.getData.get(0).getId
    val subscriptionUpdateParams = SubscriptionUpdateParams
      .builder()
      .addItem(SubscriptionUpdateParams.Item.builder().setId(subscriptionItem).setPlan(planId).build())
      .build()
    subscription.update(subscriptionUpdateParams)
    releasedSchedule
  }

  def updateSchedule(
    subscriptionScheduleId: String,
    phases: Seq[StripePhase],
    renewalBehavior: EndBehavior
  ): SubscriptionSchedule = {
    val scheduleSubscription = getRawSubscriptionSchedule(subscriptionScheduleId)
    val params = SubscriptionScheduleUpdateParams
      .builder()
      .setEndBehavior(renewalBehavior)
      .addAllPhase(phases.map(StripePhase.toPhaseUpdateParam(_)).asJava)
      .build()
    scheduleSubscription.update(params)
  }

  def backfillTaxCode(customerId: String)(implicit executionContext: ExecutionContext): Future[Customer] = {
    val customer = Customer.retrieve(customerId)
    val metadata = customer.getMetadata
    if (metadata.containsKey("TaxCode") && metadata.get("TaxCode").equals(taxCode))
      Future.successful(customer)
    else {
      val customerTaxCode = Map[String, String]("TaxCode" -> taxCode).asJava
      val customerParams = Map[String, Object]("metadata" -> customerTaxCode).asJava
      val updateFuture = Future(customer.update(customerParams))
      updateFuture
    }
  }

  def createCustomer(id: UserId, name: String, email: String, addressCountry: String)(implicit
  ec: ExecutionContext): FOption[StripeCustomer] = {
    val description = if (name.isEmpty) null else name

    val customerMetaData = Map[String, String](
      "user_id" -> id.toEntityId.value,
      "TaxCode" -> taxCode,
      "Address_Country" -> "USA"
    ).asJava

    val customerParams = Map[String, Object](
      "email" -> email,
      "description" -> description,
      "metadata" -> customerMetaData
    ).asJava

    FOption {
      Future(StripeCustomer(Customer.create(customerParams)))
    }
  }

  def backfillAddress(customerId: String)(implicit executionContext: ExecutionContext): Future[Customer] = {
    val customer = Customer.retrieve(customerId)
    val metadata = customer.getMetadata
    val optDefaultSource = Option(customer.getDefaultSource).map(customer.getSources.retrieve)
    val card = optDefaultSource match {
      case Some(c: Card) => Some(c)
      case _ => Option.empty
    }
    if (card.isEmpty)
      Future.successful(customer)
    else {
      val country = card.flatMap(c => Option(c.getCountry))
      val addressCountry = card.flatMap(c => Option(c.getAddressCountry))
      val countryToUse = if (addressCountry.isEmpty) country else addressCountry
      val city = card.flatMap(c => Option(c.getAddressCity))
      val postal = card.flatMap(c => Option(c.getAddressZip))
      val line1 = card.flatMap(c => Option(c.getAddressLine1))
      val line2 = card.flatMap(c => Option(c.getAddressLine2))
      val state = card.flatMap(c => Option(c.getAddressState))
      val billingAddress =
        BillingAddress(line1, line2, None, city, state, countryToUse.flatMap(CountryCode.fromString), postal)
      val newMetadata = billingAddress.asData match {
        case contents if contents.nonEmpty => Map[String, Object]("metadata" -> contents.asJava)
        case _ => Map.empty[String, Object]
      }
      if (metadata.containsKey("Address_Country") && countryToUse.contains(metadata.get("Address_Country"))) {
        Future.successful(customer)
      } else {
        Future(customer.update(newMetadata.asJava))
      }
    }
  }

  def makeStripeCall[R <: ApiResource](stripeCall: => R, retry: Boolean)(implicit ec: ExecutionContext): Future[R] = {
    Future {
      Thread.sleep(500)
      stripeCall
    }.recover {
      case _: RateLimitException if retry =>
        Thread.sleep(1000)
        stripeCall
    }
  }
  def makeStripeCollectionCall[R <: HasId](stripeCall: => StripeCollection[R], retry: Boolean)(implicit
  ec: ExecutionContext): Future[StripeCollection[R]] = {
    Future(stripeCall).recover {
      case _: RateLimitException if retry =>
        Thread.sleep(1000)
        stripeCall
    }
  }
}
