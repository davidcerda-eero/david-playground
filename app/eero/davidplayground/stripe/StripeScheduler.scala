//package eero.common.stripe
//
//import com.google.gson.{JsonElement, JsonNull}
//import com.stripe.exception.{InvalidRequestException, RateLimitException}
//import com.stripe.model.{Customer, Subscription, SubscriptionSchedule}
//import com.stripe.param.SubscriptionScheduleUpdateParams.RenewalBehavior
//import eero.common.stripe.data.{CancelTime, ScheduleTestParams, StripePhase, StripeSubscriptionSchedule, TestType}
//import eero.core.util.PrintingTools
//import eero.premiumsubscriptionsapi.data.PremiumInterval
//import eero.time.InstantRange
//
//import java.time.{Duration, Instant}
//import javax.inject.Singleton
//import scala.jdk.CollectionConverters._
//import scala.util.{Failure, Success, Try}
//
//sealed trait StripeIds {
//  val secureMonthlyPlan: String
//  val secureYearlyPlan: String
//  val plusMonthlyPlan: String
//  val plusYearlyPlan: String
//  val zeroPlusYearPlan: String
//  val zeroPlusMonthPlan: String
//  val halfOffCoupon: String
//}
//
//object ProductionStripeIds extends StripeIds {
//  val secureMonthlyPlan: String = "plan_FfPSRcj1QFhg3y"
//  val secureYearlyPlan: String = "plan_FfPTQNbGDAQB7A"
//  val plusMonthlyPlan: String = "price_1M2hYJBlqR9vzFLWCdoXQqEf"
//  val plusYearlyPlan: String = "price_1M2hVFBlqR9vzFLWzuGdpdAi"
//  val zeroPlusMonthPlan: String = "price_1M2hTGBlqR9vzFLWn6VTfeoT"
//  val zeroPlusYearPlan: String = "price_1M4BqTBlqR9vzFLWFuBf1oQq"
//  val halfOffCoupon: String = "DbfgIKng"
//}
//
//object StageStripeIds extends StripeIds {
//  val secureMonthlyPlan: String = "plan_Eyg6ygy47IqDzP"
//  val secureYearlyPlan: String = "plan_EygDo6rdpuxdYG"
//  val plusMonthlyPlan: String = "price_1M2in6BlqR9vzFLWOTebxrWZ"
//  val plusYearlyPlan: String = "price_1M1ytpBlqR9vzFLW6pfLP8tT"
//  val zeroPlusMonthPlan: String = "price_1Lwt2IBlqR9vzFLW4Q8uS7nZ"
//  val halfOffCoupon: String = "AH6cNupG"
//  val zeroPlusYearPlan: String = "price_1Lwt2IBlqR9vzFLW4Q8uS7nZ"
//}
//
//@Singleton
//class StripeScheduler(environment: String) {
//  val StripeIds = if (environment == "live") {
//    ProductionStripeIds
//  } else {
//    StageStripeIds
//  }
//  private val waitTime: Long = 1000 * 60 * 2
//
//  private def getStartingPlan(interval: PremiumInterval): String = interval match {
//    case PremiumInterval.Year => StripeIds.secureYearlyPlan
//    case PremiumInterval.Month => StripeIds.secureMonthlyPlan
//    case _ => ""
//  }
//
//  private def getUpgradePlan(interval: PremiumInterval): String = interval match {
//    case PremiumInterval.Year => StripeIds.plusYearlyPlan
//    case PremiumInterval.Month => StripeIds.plusMonthlyPlan
//    case _ => ""
//  }
//
//  private def getZeroPlan(interval: PremiumInterval): String = interval match {
//    case PremiumInterval.Year => StripeIds.zeroPlusYearPlan
//    case PremiumInterval.Month => StripeIds.zeroPlusMonthPlan
//    case _ => ""
//  }
//
//  def getSubscriptionWithSchedule(customer: Customer): (Option[Subscription], Option[String]) = {
//    val optSubscriptionId =
//      Option(customer.getSubscriptions).flatMap(_.autoPagingIterable().asScala.toSeq.headOption.map(_.getId))
//    val optSubscription = optSubscriptionId.map(s => Subscription.retrieve(s))
//    val optSchedule = optSubscription.flatMap(
//      s =>
//        s.getRawJsonObject.get("schedule") match {
//          case _: JsonNull => None
//          case schedule: JsonElement => Some(schedule.getAsString)
//        }
//    )
//
//    (optSubscription, optSchedule)
//  }
//
//  def resetForTest(
//    customerId: String,
//    interval: PremiumInterval,
//    nextCycle: Option[java.lang.Long],
//    backDate: Option[java.lang.Long],
//    addSchedule: Boolean,
//    cancel: Boolean
//  ): (Subscription, Option[SubscriptionSchedule]) = {
//    val basePlanId = getStartingPlan(interval)
//    val customer = Customer.retrieve(customerId)
//    val (optSubscription, optSchedule) = getSubscriptionWithSchedule(customer)
//
//    (optSubscription, optSchedule) match {
//      case (Some(sub), Some(schedule)) =>
//        println(s"Resetting subscription: ${sub.getId} and schedule: ${schedule}")
//        StripeHelper.releaseSchedule(StripeHelper.getRawSubscriptionSchedule(schedule))
//        val updatedSub = StripeHelper.updatePlan(sub, basePlanId, nextCycle)
//        resetSubscriptionAndSchedule(updatedSub, addSchedule, cancel)
//
//      case (Some(sub), None) =>
//        println(s"Resetting subscription: ${sub.getId} w/ no schedule")
//        val updatedSub = StripeHelper.updatePlan(sub, basePlanId, nextCycle)
//        resetSubscriptionAndSchedule(updatedSub, addSchedule, cancel)
//
//      case (None, _) =>
//        println(s"Creating a new subscription")
//        val newSub = StripeHelper.startSubscription(customerId, basePlanId, nextCycle, backDate)
//        resetSubscriptionAndSchedule(newSub, addSchedule, cancel)
//    }
//  }
//
//  private def resetSubscriptionAndSchedule(
//    subscription: Subscription,
//    addSchedule: Boolean,
//    cancel: Boolean
//  ): (Subscription, Option[SubscriptionSchedule]) = {
//    val canceledSub = StripeHelper.cancelSubscription(subscription, cancel)
//    val newSchedule = if (addSchedule) Some(StripeHelper.createScheduleFromSubscription(subscription.getId)) else None
//    (canceledSub.zip( newSchedule)
//  }
//
//  private def createPhase(
//    start: Instant,
//    end: Instant,
//    plan: Option[String],
//    coupon: Option[String]
//  ): StripePhase = {
//    StripePhase(plan, coupon, None, InstantRange(start, end))
//  }
//
//  private def updateSchedule(
//    schedule: SubscriptionSchedule,
//    phases: Seq[StripePhase]
//  ): SubscriptionSchedule = {
//    Try {
//      StripeHelper.updateSchedule(schedule.getId, phases, RenewalBehavior.RELEASE)
//    } match {
//      case Success(value) => value
//      case Failure(exception: InvalidRequestException) =>
//        println(exception.getMessage)
//        StripeHelper.getRawSubscriptionSchedule(schedule.getId).release()
//      case Failure(err) => throw err
//    }
//  }
//
//  private def cancelSchedule(
//    subscription: Subscription,
//    schedule: SubscriptionSchedule,
//    cancelTime: CancelTime
//  ): SubscriptionSchedule = {
//    if (cancelTime != CancelTime.CancelPreSchedule) {
//      println(s"Schedule ${schedule.getId} being set to cancel")
//      StripeHelper.cancelScheduledSubscription(subscription, schedule)
//    } else {
//      println(s"Schedule ${schedule.getId} already set to cancel")
//      schedule
//    }
//  }
//
//  private def getPhases(
//    schedule: SubscriptionSchedule,
//    subscription: Subscription,
//    upgradePlanId: String,
//    zeroDollarPlan: String,
//    coupon: String,
//    migrationStart: java.lang.Long,
//    renewalBehavior: RenewalBehavior
//  ): Seq[StripePhase] = {
//    val halfOffTime = Instant.ofEpochSecond(subscription.getCurrentPeriodEnd).plus(Duration.ofMinutes(2))
//    val timeTillNextCycle = Instant.ofEpochSecond(subscription.getCurrentPeriodEnd)
//    val oldPhase = StripePhase(schedule.getPhases.asScala.head)
//    val phaseBeforeMigration =
//      oldPhase.copy(range = oldPhase.range.copy(end = Instant.ofEpochSecond(migrationStart)))
//    val phaseMigrationToPeriodEnd =
//      createPhase(phaseBeforeMigration.range.end, timeTillNextCycle, Some(zeroDollarPlan), None)
//    val phaseHalfOff =
//      createPhase(phaseMigrationToPeriodEnd.range.end, halfOffTime, Some(upgradePlanId), Some(coupon))
//
//    renewalBehavior match {
//      case RenewalBehavior.RELEASE | RenewalBehavior.RENEW =>
//        Seq(phaseBeforeMigration, phaseMigrationToPeriodEnd, phaseHalfOff)
//      case RenewalBehavior.NONE => Seq(phaseBeforeMigration, phaseMigrationToPeriodEnd)
//    }
//  }
//
//  def runMigrationTest(
//    testParams: ScheduleTestParams,
//    migrationStart: java.lang.Long
//  ): StripeSubscriptionSchedule = {
//    val now = Instant.now()
//    val customerId = testParams.customerId
//    val interval = testParams.interval
//    val upgradePlan = getUpgradePlan(interval)
//    val zeroPlan = getZeroPlan(interval)
//    val halfOffCoupon = StripeIds.halfOffCoupon
//    val testToRun = testParams.testType
//    val cancelTime = testParams.cancelTime
//    val nextCycle = testParams.nextCycle
//    val backdate = testParams.backdate
//
//    val (subscription, optSchedule) = cancelTime match {
//      case CancelTime.NoCancel | CancelTime.CancelPostSchedule =>
//        resetForTest(customerId, interval, nextCycle, backdate, true, false)
//      case CancelTime.CancelPreSchedule =>
//        resetForTest(customerId, interval, nextCycle, backdate, true, true)
//    }
//
//    val schedule = optSchedule.get
//    val phases =
//      getPhases(schedule, subscription, upgradePlan, zeroPlan, halfOffCoupon, migrationStart, RenewalBehavior.RELEASE)
//    val halfOffTime = 2
//
//    val updatedSchedule = updateSchedule(schedule, phases)
//    val finishedSchedule = testToRun match {
//      case TestType.HappyPath => updatedSchedule
//
//      case TestType.CancelAndReactivateBeforeMigration =>
//        val canceledSchedule = StripeHelper.cancelScheduledSubscription(subscription, updatedSchedule)
//        StripeHelper.reactivateSchedule(canceledSchedule, Some(upgradePlan), Some(halfOffCoupon), halfOffTime)
//
//      case TestType.CancelBeforeAndReactivateAfterMigration =>
//        val canceledSchedule = cancelSchedule(subscription, updatedSchedule, cancelTime)
//        val pause = (migrationStart - now.getEpochSecond) * 1000 + waitTime
//        println(s"Waiting for ${pause / (60 * 1000)} minutes before reactivating after migration")
//        Thread.sleep(pause)
//        val canceledAndMigratedSchedule = StripeHelper.getRawSubscriptionSchedule(canceledSchedule.getId)
//        StripeHelper.reactivateSchedule(
//          canceledAndMigratedSchedule,
//          Some(upgradePlan),
//          Some(halfOffCoupon),
//          halfOffTime
//        )
//
//      case TestType.CancelAndReactivateAfterMigration =>
//        val pause = (migrationStart - now.getEpochSecond) * 1000 + waitTime
//        println(s"Waiting for ${pause / (60 * 1000)} minutes before cancelling and reactivating after migration")
//        Thread.sleep(pause)
//        val canceledSchedule = cancelSchedule(subscription, updatedSchedule, cancelTime)
//        Thread.sleep(waitTime)
//        StripeHelper.reactivateSchedule(canceledSchedule, Some(upgradePlan), Some(halfOffCoupon), halfOffTime)
//
//      case TestType.UpgradeBefore =>
//        StripeHelper.updateScheduledSubscription(subscription, schedule, upgradePlan)
//
//      case TestType.MonthlyUpgradeAfter =>
//        Thread.sleep(migrationStart - now.getEpochSecond + waitTime)
//        updatedSchedule
//
//      case TestType.CanceledNeverReactivated =>
//        cancelSchedule(subscription, updatedSchedule, cancelTime)
//    }
////    println(PrintingTools.specialPrint(Json.prettyPrint(Json.parse(finishedSchedule.getRawJsonObject.toString))))
//
////    if (updatedSchedule.getStatus != "released") {
////      val lastPhaseEnd =
////        updatedSchedule.getPhases.asScala.takeRight(1).headOption.fold(now.getEpochSecond)(_.getEndDate)
////      Thread.sleep(lastPhaseEnd - now.getEpochSecond + waitTime)
////      StripeHelper.getRawSubscriptionSchedule(updatedSchedule.getId).release()
////    }
//    val ret = StripeSubscriptionSchedule(finishedSchedule)
//    println(PrintingTools.specialPrint(StripeSubscriptionSchedule(finishedSchedule)))
//    ret
//  }
//
//  def migrateCustomer(
//    customerId: String,
//    migrationSlot: java.lang.Long,
//    migrationStart: java.lang.Long
//  ): (String, String, Long) = {
//    val (customer, optSubscription, optSchedule) = Try {
//      getStripeData(customerId)
//    } match {
//      case Success(value) => value
//      case Failure(exception: RateLimitException) =>
//        Thread.sleep(10 * 1000)
//        println("Waiting 10 seconds for rate limit")
//        println(exception.getMessage)
//        getStripeData(customerId)
//      case Failure(exception) => throw exception
//    }
//
//    val status = (optSubscription, optSchedule) match {
//      case (None, _) => "no_subscription"
//      case (Some(sub), Some(schedule)) => checkAndFixSchedule(sub, schedule, migrationSlot, migrationStart)
//      case (Some(sub), None) =>
//        val schedule = StripeHelper.createScheduleFromSubscription(sub.getId)
//        makeSchedule(sub, schedule, migrationSlot, migrationStart)
//    }
//
//    (customerId, status, migrationSlot)
//  }
//
//  def getStripeData(customerId: String): (Customer, Option[Subscription], Option[String]) = {
//    val customer = Customer.retrieve(customerId)
//    val (optSubscription, optSchedule) = getSubscriptionWithSchedule(customer)
//    (customer, optSubscription, optSchedule)
//  }
//
//  def checkSchedule(
//    scheduleId: String
//  ): Boolean = {
//    val schedule = StripeHelper.getRawSubscriptionSchedule(scheduleId)
//    val phases = schedule.getPhases.asScala.toSeq
//    if (phases.size <= 1) {
//      true
//    } else {
//      false
//    }
//  }
//
//  def checkAndFixSchedule(
//    subscription: Subscription,
//    scheduleId: String,
//    migrationSlot: java.lang.Long,
//    migrationStart: java.lang.Long
//  ): String = {
//    val schedule = StripeHelper.getRawSubscriptionSchedule(scheduleId)
//    val phases = schedule.getPhases.asScala.toSeq
//    if (phases.size <= 1) {
//      makeSchedule(subscription, schedule, migrationSlot, migrationStart)
//      "success"
//    } else {
//      "already_scheduled"
//    }
//  }
//
//  def makeSchedule(
//    subscription: Subscription,
//    schedule: SubscriptionSchedule,
//    migrationSlot: java.lang.Long,
//    migrationStart: java.lang.Long
//  ): String = {
//    val scheduleErrorMessage = "You cannot migrate a subscription that is already attached to a schedule"
//    Try {
//      if (subscription.getCancelAtPeriodEnd == true && subscription.getCurrentPeriodEnd < migrationStart) {
//        "cancels_before"
//      } else {
//        val plan = subscription.getPlan
//        val interval = PremiumInterval.fromIntervalStringCaseInsensitive(plan.getInterval)
//        val upgradePlan = getUpgradePlan(interval)
//        val zeroDollarPlan = getZeroPlan(interval)
//        val halfOffCoupon = StripeIds.halfOffCoupon
//        val renewalBehavior = if (subscription.getCancelAtPeriodEnd) RenewalBehavior.NONE else RenewalBehavior.RELEASE
//        val phases =
//          getPhases(schedule, subscription, upgradePlan, zeroDollarPlan, halfOffCoupon, migrationSlot, renewalBehavior)
//        StripeSubscriptionSchedule(StripeHelper.updateSchedule(schedule.getId, phases, renewalBehavior))
//        "success"
//      }
//    } match {
//      case Success(value) => value
//      case Failure(exception: RateLimitException) =>
//        Thread.sleep(10 * 1000)
//        println("Waiting 10 seconds for rate limit")
//        println(exception.getMessage)
//        "rate_limit"
//      case Failure(exception: InvalidRequestException) if exception.getMessage.contains(scheduleErrorMessage) =>
//        "already_scheduled"
//      case Failure(exception: InvalidRequestException) =>
//        println(exception.getMessage)
//        "invalid_request"
//      case Failure(exception) => throw exception
//    }
//  }
//
//}
