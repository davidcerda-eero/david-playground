//package eero.davidplayground.stripe
//
//import akka.actor.ActorSystem
//import akka.dispatch.MessageDispatcher
//import com.stripe.exception.RateLimitException
//import com.stripe.Stripe
//import eero.davidplayground.csv.CSVProcessor
//
//import scala.concurrent.{Await, Future}
//import scala.concurrent.duration.{Duration, MINUTES}
//import scala.util.{Failure, Success}
//
//object TaxCodeFixer {
//  def main(args: Array[String]): Unit = {
//    Stripe.apiKey = ""
//    val system = ActorSystem.create()
//    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
//    //    //    val scheduler = new StripeScheduler("live")
//    val csvLiaison = new CSVProcessor(
//      "Tax-Code-Fix-10-2023.csv",
//      Some("Tax-Code-Fix-10-2023-Result.csv"),
//      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/CSV_Files/",
//      "data/",
//      "result/"
//    )
//    val customers = csvLiaison.getColumn("customer_id")
//
//    val customersFuture = Future.sequence(customers.toList.tail.map { cus =>
//      Thread.sleep(1000)
//      StripeHelper.backfillTaxCode(cus).map((cus, _)).recoverWith {
//        case _: RateLimitException =>
//          Thread.sleep(1000)
//          StripeHelper.backfillTaxCode(cus).map((cus, _))
//      }
//    })
//
//    Await.ready(customersFuture, Duration.apply(120, MINUTES)).onComplete {
//      case Success(value) =>
//        csvLiaison.writeRow("customer_id,updated")
//        value.map { case (customer, updated) =>
//          csvLiaison.writeRow(s"$customer,$updated")
//        }
//        csvLiaison.closeFile()
//        println("Done")
//      case Failure(exception) => throw exception
//    }
//    //    )
//    //
//    //    val data = csvLiaison.asRows("customer_id", "status")
//    //    val dataMap = data.map(_.split(",")).groupBy(_(1))
//    //
//    //    val alreadyScheduled = csvLiaison.createWriteFile("last_customer_already_scheduled.csv")
//    //    alreadyScheduled.write("customer_id\n")
//    //    val noSubscription = csvLiaison.createWriteFile("last_customer_no_subscription.csv")
//    //    noSubscription.write("customer_id\n")
//    //    dataMap
//    //      .get("already_scheduled")
//    //      .map(_.map { rows =>
//    //        alreadyScheduled.write(rows(0) + "\n")
//    //
//    //      })
//    //    dataMap
//    //      .get("no_subscription")
//    //      .map(_.map { rows =>
//    //        noSubscription.write(rows(0) + "\n")
//    //      })
//    //    alreadyScheduled.close()
//    //    noSubscription.close()
//    //    //
//    //    //    val data = csvLiaison.getColumn("customer_id")
//    //    //    csvLiaison.writeRow("customer_id,status")
//    //    //    data.map { customer =>
//    //    //      val status = {
//    //    //        val now = Instant.now().getEpochSecond
//    //    //        val inFive = Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond
//    //    //        val (_, optSub, optSch) = scheduler.getStripeData(customer)
//    //    //        (optSub, optSch) match {
//    //    //          case (Some(sub), Some(sch)) =>
//    //    //            scheduler.checkAndFixSchedule(sub, sch, inFive, now)
//    //    //          case (Some(sub), None) =>
//    //    //            val schedule = StripeHelper.createScheduleFromSubscription(sub.getId)
//    //    //            scheduler.makeSchedule(sub, schedule, inFive, now)
//    //    //            "was_not_scheduled"
//    //    //          case _ => "no_subscription"
//    //    //        }
//    //    //      }
//    //    //      println(s"$customer,$status")
//    //    //      csvLiaison.writeRow(s"$customer,$status")
//    //    //    }
//    //    //    csvLiaison.closeFile()
//    //    //  }
//  }
//}
