package eero.davidplayground.stripe

import com.stripe.Stripe
import com.stripe.model.{Price, PriceCollection}
import eero.premiumsubscriptionsapi.data.{PremiumInterval, PremiumTierString}
import eero.premiumsubscriptionsapi.data.PremiumTierString.{Premium, PremiumPlus}

import java.io.{BufferedWriter, FileWriter}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.IterableHasAsScala

object StripePlanProcessor {
  def main(args: Array[String]): Unit = {
    Stripe.apiKey = ""

    class PriceProcessor(val prices: Seq[Price]) {
      def ++(that: PriceProcessor): PriceProcessor = {
        new PriceProcessor(prices ++ that.prices)
      }

      def getTier(price: Price): PremiumTierString = {
        PremiumTierString.fromStringIgnoreCaseOrDefault(
          Option(price.getMetadata)
            .flatMap(m => Option(m.get("tier")))
            .getOrElse("no-tier")
        )
      }
      def onlyRetail(): PriceProcessor = {
        val filteredPrices = prices.filter { price =>
          val optName = Option(price.getNickname)
          if (optName.nonEmpty) {
            val name = optName.get
            val isEssentials = name.toLowerCase.contains("essentials")
            val isISP = name.toLowerCase.contains("isp")
            val isAuto = name.toLowerCase.contains("auto")
            val isProtectPro = name.toLowerCase.contains("protect")
            val isCompNonMigration = name.toLowerCase.contains("complimentary") && !name.toLowerCase.contains(
              "migration"
            )
            val isTest = name.toLowerCase.contains("test")
            val isBeta = name.toLowerCase.contains("beta")
            val billing = Option(price.getMetadata)
              .flatMap(m => Option(m.get("billing")).map(_.toBoolean))
              .getOrElse(true)
            val priceType = Option(price.getType)
            billing && !isProtectPro && !isCompNonMigration && !isBeta && !isISP && !isEssentials && !isAuto && !isTest && priceType
              .contains("recurring")
          } else {
            false
          }
        }
        val retail = new PriceProcessor(filteredPrices)
        retail.onlyOfTier(Premium) ++ retail.onlyOfTier(PremiumPlus)
      }

      def onlyOfTier(desiredTier: PremiumTierString): PriceProcessor = {
        val filteredPrices = prices.filter { price =>
          val name = price.getNickname
          val isCompMigration = name.toLowerCase.contains("complimentary") && name.toLowerCase.contains(
            "migration"
          )
          val tier = getTier(price)
          isCompMigration || tier == desiredTier
        }
        new PriceProcessor(filteredPrices)
      }

      def onlyUS(): PriceProcessor = {
        val filteredPrices = prices.filter { price =>
          val currency = price.getCurrency
          currency == "usd"
        }
        new PriceProcessor(filteredPrices)
      }

      def onlyIntl(): PriceProcessor = {
        val filteredPrices = prices.filter { price =>
          val currency = price.getCurrency
          currency != "usd"
        }
        new PriceProcessor(filteredPrices)
      }

      def onlyInterval(targetInterval: PremiumInterval): PriceProcessor = {
        val filteredPrices = prices.filter { price =>
          val interval = PremiumInterval.fromIntervalStringCaseInsensitive(price.getRecurring.getInterval)
          targetInterval == interval
        }
        new PriceProcessor(filteredPrices)
      }

      val sqlString = (str: String) =>
          s"\nWhere plan_id in (${str})"

      def makeSqlString(): String = {
        sqlString(prices.map("'" + _.getId + "'").mkString(","))
      }

      def printPrice(): Unit = {
        prices.foreach(price => println(s"${getTier(price)},${price.getId},${price.getNickname}"))
      }
    }

    def getPlansTillDone(): Seq[Price] = {
      @tailrec
      def getPlans(plans: PriceCollection, previousData: Seq[Price]): Seq[Price] = {
        val currentPlans = plans.autoPagingIterable().asScala
        val hasMore = plans.getHasMore
        val optLastPlanId = currentPlans.lastOption.map(_.getId)
        if (!hasMore) {
          previousData ++ currentPlans
        } else {
          val nextPlans = StripeHelper.getPrices(optLastPlanId)
          getPlans(nextPlans, previousData ++ currentPlans)
        }
      }

      val initialEvents = StripeHelper.getPrices(None)
      getPlans(initialEvents, Seq.empty)
    }

    val file = new BufferedWriter(
      new FileWriter(
        "/local/home/cerdadav/eero/cloud/modules/davidplayground/test/eero/davidplayground/CSV_Files/retailPlans.csv"
      )
    )
    file.write("plan_id\n")
    val prices = new PriceProcessor(getPlansTillDone())
    val retail = prices.onlyRetail()
    val retailUS = retail.onlyUS()
    val retailIntl = retail.onlyIntl()
    println("Secure_US_Yearly:" + retailUS.onlyInterval(PremiumInterval.Year).onlyOfTier(Premium).makeSqlString())
    println("Plus_US_Yearly:" + retailUS.onlyInterval(PremiumInterval.Year).onlyOfTier(PremiumPlus).makeSqlString())
    println("Secure_US_Monthly:" + retailUS.onlyInterval(PremiumInterval.Month).onlyOfTier(Premium).makeSqlString())
    println("Plus_US_Monthly:" + retailUS.onlyInterval(PremiumInterval.Month).onlyOfTier(PremiumPlus).makeSqlString())
    println("Secure_Intl_Yearly:" + retailIntl.onlyInterval(PremiumInterval.Year).onlyOfTier(PremiumPlus).makeSqlString())
    println("Plus_Intl_Yearly:" + retailIntl.onlyInterval(PremiumInterval.Month).onlyOfTier(Premium).makeSqlString())
    println("Secure_Intl_Monthly:" + retailIntl.onlyInterval(PremiumInterval.Month).onlyOfTier(PremiumPlus).makeSqlString())
    println("Plus_Intl_Monthly:" + retailIntl.onlyInterval(PremiumInterval.Year).onlyOfTier(Premium).makeSqlString())
    file.close()
  }
}
