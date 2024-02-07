package eero.davidplayground.SQL_makers

import eero.davidplayground.csv.CSVProcessor

object CreateUpdateToCA {
  def main(args: Array[String]): Unit = {
    val cableOneCAMissingUserId = new CSVProcessor(
      "Case_B.1_-_Cable_One_CA_missing_user_id_2024_02_02.csv",
      Some("UpdateCableOne.sql"),
      "inc-620-cable-one-subs"
    )
    val (unique, _) = cableOneCAMissingUserId.separateDuplicates("user_id")
    val sqlStatementCreator = new CreateSQL(unique)
    val customerAccountIdName = "customer_account_id"
    val userIdName = "user_id"
    val updateColumns =
      Seq(
        UpdateColumn(customerAccountIdName, IntColumn, "", WhereClause),
        UpdateColumn(userIdName, IntColumn, "", SetClause)
      )
    val statement =
      sqlStatementCreator.createUpdateSQL("customer_accounts")(updateColumns) {
        (indexes, row) =>
          val createUpdateColumn =
            (name: String, columnType: ColumnType) =>
              UpdateColumn(name, columnType, row(indexes(name)))
          val customerAccountId = createUpdateColumn(customerAccountIdName, IntColumn)
          val userId = createUpdateColumn(userIdName, IntColumn)
          val values =
            Seq(customerAccountId, userId)
          Option(values)
      }
    cableOneCAMissingUserId.write(sqlStatementCreator.createTransaction(statement))
    cableOneCAMissingUserId.closeFile()
  }

}
