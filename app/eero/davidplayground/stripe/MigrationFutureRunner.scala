//package eero.davidplayground.stripe
//
//import akka.actor.ActorSystem
//import akka.dispatch.MessageDispatcher
//import com.stripe.Stripe
//import eero.davidplayground.MigrationSlotWithLock
//import eero.davidplayground.csv.CSVProcessorWithLock
//
//import java.time.{Instant, ZoneId}
//import scala.concurrent.duration.{Duration, MINUTES}
//import scala.concurrent.{Await, Future}
//import scala.util.{Failure, Success}
//
//object MigrationFutureRunner {
//  def main(args: Array[String]) = {
//    Stripe.apiKey = "live_key"
//    val system = ActorSystem.create()
//    implicit val context: MessageDispatcher = system.dispatchers.lookup("contexts.stripe")
//    val scheduler = new StripeSchedulerFuture("live", system)
//    val csvLiaison = new CSVProcessorWithLock(
//      "MigrationQueryThird.csv",
//      Some("MigrationFutureResultsThird.csv"),
//      "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/CSV_Files/"
//    )
//    val start = Instant.now().plus(java.time.Duration.ofMinutes(60))
//    val migrationSlots = new MigrationSlotWithLock(start)
//
//    println(start.atZone(ZoneId.of("UTC-8")))
//    csvLiaison.writeRow("customer_id,status,time")
//
//    val data = csvLiaison.getColumn("customer_id")
//
//    def runMigrationFuture(): Unit = {
//      def doFutureStuff[T](fit: Iterable[Future[T]]): Unit = {
//        val futureWork = for {
//          res <- Future.sequence(fit)
//        } yield res
//
//        Await.ready(futureWork, Duration.apply(120, MINUTES)).onComplete {
//          case Success(value) =>
//            csvLiaison.closeFile()
//            value.foreach(println)
//          case Failure(exception) => throw exception
//        }
//      }
//
//      doFutureStuff {
//        data.map { customer =>
//          val slot = migrationSlots.choseSlot()
//          scheduler.migrateCustomerFuture(customer, slot, migrationSlots.migrationStartEpoch).map {
//            case (cid, status, time) =>
//              csvLiaison.writeRow(s"$cid,$status,$time")
//              (cid, status, time)
//          }
//        }
//      }
//    }
//
//    runMigrationFuture()
//    println("Done")
//  }
//}
