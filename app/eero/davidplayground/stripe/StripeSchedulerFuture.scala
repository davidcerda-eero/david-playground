import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.google.gson.{JsonElement, JsonNull}
import com.stripe.exception.{InvalidRequestException, RateLimitException}
import com.stripe.model.{Customer, Subscription, SubscriptionSchedule}
import com.stripe.param.SubscriptionScheduleUpdateParams.EndBehavior
import eero.common.stripe.data.{StripePhase, StripeSubscriptionSchedule}
import eero.common.time.InstantRange
import eero.davidplayground.stripe.StripeHelper
import eero.premiumsubscriptionsapi.data.PremiumInterval

import java.time.{Duration, Instant}
import java.util.concurrent.locks.ReentrantLock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

sealed trait StripeIds {
  val secureMonthlyPlan: String
  val secureYearlyPlan: String
  val plusMonthlyPlan: String
  val plusYearlyPlan: String
  val zeroPlusYearPlan: String
  val zeroPlusMonthPlan: String
  val halfOffCoupon: String
}

object ProductionStripeIds extends StripeIds {
  val secureMonthlyPlan: String = "plan_FfPSRcj1QFhg3y"
  val secureYearlyPlan: String = "plan_FfPTQNbGDAQB7A"
  val plusMonthlyPlan: String = "price_1M2hYJBlqR9vzFLWCdoXQqEf"
  val plusYearlyPlan: String = "price_1M2hVFBlqR9vzFLWzuGdpdAi"
  val zeroPlusMonthPlan: String = "price_1M2hTGBlqR9vzFLWn6VTfeoT"
  val zeroPlusYearPlan: String = "price_1M4BqTBlqR9vzFLWFuBf1oQq"
  val halfOffCoupon: String = "DbfgIKng"
}

object StageStripeIds extends StripeIds {
  val secureMonthlyPlan: String = "plan_Eyg6ygy47IqDzP"
  val secureYearlyPlan: String = "plan_EygDo6rdpuxdYG"
  val plusMonthlyPlan: String = "price_1M2in6BlqR9vzFLWOTebxrWZ"
  val plusYearlyPlan: String = "price_1M1ytpBlqR9vzFLW6pfLP8tT"
  val zeroPlusMonthPlan: String = "price_1Lwt2IBlqR9vzFLW4Q8uS7nZ"
  val halfOffCoupon: String = "AH6cNupG"
  val zeroPlusYearPlan: String = "price_1Lwt2IBlqR9vzFLW4Q8uS7nZ"
}
@Singleton
class StripeSchedulerFuture @Inject() (environment: String, actorSystem: ActorSystem) {
  implicit val context: MessageDispatcher = actorSystem.dispatchers.lookup("contexts.stripe")
  val StripeIds = if (environment == "live") {
    ProductionStripeIds
  } else {
    StageStripeIds
  }

  private val lock = new ReentrantLock()

  private def getUpgradePlan(interval: PremiumInterval): String = interval match {
    case PremiumInterval.Year => StripeIds.plusYearlyPlan
    case PremiumInterval.Month => StripeIds.plusMonthlyPlan
    case _ => ""
  }

  private def getZeroPlan(interval: PremiumInterval): String = interval match {
    case PremiumInterval.Year => StripeIds.zeroPlusYearPlan
    case PremiumInterval.Month => StripeIds.zeroPlusMonthPlan
    case _ => ""
  }

  def getSubscriptionWithSchedule(customer: Customer): (Option[Subscription], Option[String]) = {
    val optSubscriptionId =
      Option(customer.getSubscriptions).flatMap(_.autoPagingIterable().asScala.toSeq.headOption.map(_.getId))
    val optSubscription = optSubscriptionId.map(s => Subscription.retrieve(s))
    val optSchedule = optSubscription.flatMap(
      s =>
        s.getRawJsonObject.get("schedule") match {
          case _: JsonNull => None
          case schedule: JsonElement => Some(schedule.getAsString)
        }
    )

    (optSubscription, optSchedule)
  }

  private def createPhase(
    start: Instant,
    end: Instant,
    plan: Option[String],
    coupon: Option[String]
  ): StripePhase = {
    StripePhase(plan, coupon, None, InstantRange(start, end))
  }

  private def getPhases(
    schedule: SubscriptionSchedule,
    subscription: Subscription,
    upgradePlanId: String,
    zeroDollarPlan: String,
    coupon: String,
    migrationStart: java.lang.Long,
    renewalBehavior: EndBehavior
  ): Seq[StripePhase] = {
    val halfOffTime = Instant.ofEpochSecond(subscription.getCurrentPeriodEnd).plus(Duration.ofMinutes(2))
    val timeTillNextCycle = Instant.ofEpochSecond(subscription.getCurrentPeriodEnd)
    val oldPhase = StripePhase(schedule.getPhases.asScala.head)
    val phaseBeforeMigration =
      oldPhase.copy(range = oldPhase.range.copy(end = Instant.ofEpochSecond(migrationStart)))
    val phaseMigrationToPeriodEnd =
      createPhase(phaseBeforeMigration.range.end, timeTillNextCycle, Some(zeroDollarPlan), None)
    val phaseHalfOff =
      createPhase(phaseMigrationToPeriodEnd.range.end, halfOffTime, Some(upgradePlanId), Some(coupon))

    renewalBehavior match {
      case EndBehavior.RELEASE | EndBehavior.RENEW =>
        Seq(phaseBeforeMigration, phaseMigrationToPeriodEnd, phaseHalfOff)
      case EndBehavior.NONE => Seq(phaseBeforeMigration, phaseMigrationToPeriodEnd)
      case _ => Seq(phaseBeforeMigration, phaseMigrationToPeriodEnd)
    }
  }

  def fakeMigrateCustomerFuture(
    customerId: String,
    migrationSlot: java.lang.Long,
    migrationStart: java.lang.Long
  ): Future[(String, String, java.lang.Long)] = {
    Future.successful((customerId, "success", migrationSlot))
  }

  def migrateCustomerFuture(
    customerId: String,
    migrationSlot: java.lang.Long,
    migrationStart: java.lang.Long
  ): Future[(String, String, java.lang.Long)] = {
    val scheduleErrorMessage = "You cannot migrate a subscription that is already attached to a schedule"

    def getStripeData(): (Customer, Option[Subscription], Option[String]) = {
      val customer = Customer.retrieve(customerId)
      Thread.sleep(100)
      val (optSubscription, optSchedule) = getSubscriptionWithSchedule(customer)
      (customer, optSubscription, optSchedule)
    }

    (for {
      (customer, optSubscription, optSchedule) <- Future { getStripeData() }

      status <- (optSubscription, optSchedule) match {
        case (None, _) => Future.successful("no_subscription")
        case (Some(sub), Some(schedule)) => checkAndFixSchedule(sub, schedule, migrationSlot, migrationStart)
        case (Some(sub), None) =>
          StripeHelper.createScheduleFromSubscription(sub.getId).flatMap { schedule =>
            makeScheduleFuture(sub, schedule, migrationSlot, migrationStart)
          }
      }
    } yield {
      (customerId, status, migrationSlot)
    }).recover {
      case exception: RateLimitException =>
        lock.lock()
        try {
          println("Waiting 10 seconds for rate limit")
          Thread.sleep(10 * 1000)
        } finally {
          lock.unlock()
        }
        (customerId, "rate_limit", migrationSlot)
      case exception: InvalidRequestException if exception.getMessage.contains(scheduleErrorMessage) =>
        (customerId, "already_scheduled", migrationSlot)
      case exception: InvalidRequestException =>
        exception.getMessage
        (customerId, "invalid_request", migrationSlot)
    }
  }

  private def makeScheduleFuture(
    subscription: Subscription,
    schedule: SubscriptionSchedule,
    migrationSlot: java.lang.Long,
    migrationStart: java.lang.Long
  ): Future[String] = {
    Future {
      if (subscription.getCancelAtPeriodEnd == true && subscription.getCurrentPeriodEnd < migrationStart) {
        "cancels_before"
      } else {
        val plan = subscription.getPlan
        val interval = PremiumInterval.fromIntervalStringCaseInsensitive(plan.getInterval)
        val upgradePlan = getUpgradePlan(interval)
        val zeroDollarPlan = getZeroPlan(interval)
        val halfOffCoupon = StripeIds.halfOffCoupon
        val renewalBehavior = if (subscription.getCancelAtPeriodEnd) EndBehavior.NONE else EndBehavior.RELEASE
        val phases =
          getPhases(schedule, subscription, upgradePlan, zeroDollarPlan, halfOffCoupon, migrationSlot, renewalBehavior)
        StripeSubscriptionSchedule(StripeHelper.updateSchedule(schedule.getId, phases, renewalBehavior))
        "success"
      }
    }
  }

  private def checkAndFixSchedule(
    subscription: Subscription,
    scheduleId: String,
    migrationSlot: java.lang.Long,
    migrationStart: java.lang.Long
  ): Future[String] = {
    val schedule = StripeHelper.getRawSubscriptionSchedule(scheduleId)
    val phases = StripeHelper.getRawSubscriptionSchedule(scheduleId).getPhases.asScala.toSeq
    if (phases.size <= 1) {
      makeScheduleFuture(subscription, schedule, migrationSlot, migrationStart).map(_ => "success")
    } else {
      Future.successful("already_scheduled")
    }
  }

}
