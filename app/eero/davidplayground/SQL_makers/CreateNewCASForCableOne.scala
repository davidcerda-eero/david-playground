//package eero.davidplayground.SQL_makers
//
//import eero.data.premium.CustomerAccountSubscriptionStatus
//import eero.davidplayground.csv.CSVProcessor
//import eero.premiumsubscriptionsapi.models.PartnerEeroPremiumPlan
//
//import java.time.Instant
//
//object CreateNewCASForCableOne {
//  def main(args: Array[String]): Unit = {
//    val cableOneSubscriptionsWithMissingCAS = new CSVProcessor(
//      "Case_B.1_new_cas_rows.csv",
//      Some("CreateCableOneCAS_B1.sql"),
//      "inc-620-cable-one-subs"
//    )
//    val (unique, _) = cableOneSubscriptionsWithMissingCAS.separateDuplicates("customer_account_id")
//    val sqlStatementCreator = new CreateSQL(unique)
//    val customerAccountIdName = "customer_account_id"
//    val userSubIdName = "user_subscription_id"
//    val orgId = 97554
//    val dataColumns = Seq(customerAccountIdName, userSubIdName)
//    val insertColumns = Seq(
//      "customer_account_id",
//      "user_subscription_id",
//      "organization_id",
//      "status",
//      "plan",
//      "created"
//    )
//    val statement = sqlStatementCreator.createInsertSQL("customer_account_subscriptions")(dataColumns, insertColumns) {
//      (indexes, row) =>
//        val createInsertColumn =
//          (name: String, columnType: ColumnType) => InsertColumn(name, columnType, row(indexes(name)))
//        val customerAccountId = createInsertColumn(customerAccountIdName, IntColumn)
//        val userSubscriptionId = createInsertColumn(userSubIdName, IntColumn)
//        val values =
//          Seq(
//            customerAccountId,
//            userSubscriptionId,
//            InsertColumn("organization_id", IntColumn, orgId),
//            InsertColumn("status", StringColumn, CustomerAccountSubscriptionStatus.Active.toString),
//            InsertColumn("plan", StringColumn, PartnerEeroPremiumPlan.Secure),
//            InsertColumn("created", TimeColumn, Option.empty[Instant])
//          )
//        Option(values)
//    }
//    cableOneSubscriptionsWithMissingCAS.write(sqlStatementCreator.createTransaction(statement))
//    cableOneSubscriptionsWithMissingCAS.closeFile()
//  }
//}
