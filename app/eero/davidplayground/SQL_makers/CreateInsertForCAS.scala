package eero.davidplayground.SQL_makers

import eero.data.premium.CustomerAccountSubscriptionStatus
import eero.davidplayground.csv.CSVProcessor
import eero.premiumsubscriptionsapi.models.PartnerEeroPremiumPlan.Secure

import java.time.Instant

object CreateInsertForCAS {
  def main(args: Array[String]): Unit = {
    val cableOneSubscriptionsWithMissingCA = new CSVProcessor(
      "Case_A.1_Cable_One_Subs_w_CA_No_CAS_02_01.csv",
      Some("CreateCableOneCAS.sql"),
      "inc-620-cable-one-subs"
    )
    val sqlStatementCreator = new CreateSQL(cableOneSubscriptionsWithMissingCA)
    val customerAccountIdName = "customer_account_id"
    val userSubIdName = "user_sub_id"
    val tierName = "tier"
    val planName = "plan_id"
    val orgId = 97554
    val dataColumns = Seq("customer_account_id", "user_sub_id", "tier", "plan_id")
    val insertColumns = Seq(
      "customer_account_id",
      "user_subscription_id",
      "organization_id",
      "status",
      "plan",
      "created"
    )
    val statement = sqlStatementCreator.createInsertSQL("customer_account_subscriptions")(dataColumns, insertColumns) {
      (indexes, row) =>
        val createInsertColumn =
          (name: String, columnType: ColumnType) => InsertColumn(name, columnType, row(indexes(name)))
        val customerAccountId = createInsertColumn(customerAccountIdName, IntColumn)
        val userSubscriptionId = createInsertColumn(userSubIdName, IntColumn)
        val tier = row(indexes(tierName))
        val plan = row(indexes(planName))
        if (plan != "price_1MrnluBlqR9vzFLWsKo1Q118" || tier == "premium-plus") {
          val casPlan = InsertColumn("plan", StringColumn, Secure.toString)
          val values: Seq[InsertColumn[_]] =
            Seq(
              customerAccountId,
              userSubscriptionId,
              InsertColumn("organization_id", IntColumn, orgId),
              InsertColumn("status", StringColumn, CustomerAccountSubscriptionStatus.Active.toString),
              casPlan,
              InsertColumn("created", TimeColumn, Option.empty[Instant])
            )
          Option(values)
        } else {
          None
        }
    }
    cableOneSubscriptionsWithMissingCA.write(sqlStatementCreator.createTransaction(statement))
    cableOneSubscriptionsWithMissingCA.closeFile()
  }
}
