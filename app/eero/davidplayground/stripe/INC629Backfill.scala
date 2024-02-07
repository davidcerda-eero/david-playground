package eero.davidplayground.stripe

import eero.davidplayground.csv.CSVProcessor

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.stripe.Stripe
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, MINUTES}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success}

object INC629Backfill {
  def main(args: Array[String]): Unit = {

    println(s"${Console.GREEN}Started at ${ZonedDateTime.now(ZoneId.systemDefault())}${Console.WHITE_B}")
    val system = ActorSystem.create()
    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
    val subscriptionsToGrantPlus = new CSVProcessor(
      "data_to_backfill.csv",
      Some("inc_629_backfill_res.txt")
    )

    Stripe.apiKey = readLine("Input live key:\n")
    val futureWork = subscriptionsToGrantPlus.map { subRows =>
        StripeHelper.subscribe(subRows("planId"), false, None, false, None, subRows("stripeId"))
    }
    val subsFuture = Future.sequence(futureWork)

    Await.ready(subsFuture, Duration.apply(120, MINUTES)).onComplete {
      case Success(value) =>
          value.map { sub =>
            val subId = sub.id
            val plan = sub.plan.id
            val customer = sub.optCustomerId.getOrElse("")
            subscriptionsToGrantPlus.writeRow(
              s"$subId,$plan,$customer"
            )
          }
        subscriptionsToGrantPlus.closeFile()
        println(s"${Console.GREEN}Finshed at ${ZonedDateTime.now((ZoneId.systemDefault()))}")
      case Failure(exception) => throw exception
    }
  }

}
