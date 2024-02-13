package eero.davidplayground

import eero.core.util.{StringEnum, StringEnumEntry}
import eero.data.user.permissions.{BusinessOwner, UserRole}
import eero.davidplayground.csv.{BasicWriter, CSVProcessor}

import enumeratum.PlayJsonEnum

object CSVCompare {
  def main(args: Array[String]): Unit = {
    sealed trait IneligibilityReason extends StringEnumEntry {
      def id: Int = IneligibilityReason.indexOf(this)
    }
    object IneligibilityReason extends StringEnum[IneligibilityReason] with PlayJsonEnum[IneligibilityReason] {
      lazy val values: IndexedSeq[IneligibilityReason] = findValues

      case object NoNetwork extends IneligibilityReason
      case object NoAssociatedUserWithOwnedNetwork extends IneligibilityReason

      case object BusinessOwner extends IneligibilityReason
      case object IspRole extends IneligibilityReason
      case object NoCurrentSubscription extends IneligibilityReason
      case object AlreadyPlus extends IneligibilityReason
      case object SubscriptionNotCableOneAssociated extends IneligibilityReason
      case object WrongPlanForSubscription extends IneligibilityReason
      case object NoUser extends IneligibilityReason
      case object Eligible extends IneligibilityReason
    }

    val CableOnePartnerIdsWithSerials = new CSVProcessor(
      "Investigation_Cable_Partner_Ids.csv",
      None,
      "inc-620-cable-one-subs"
    )

    val CableOneSerialsWithNetworkId = new CSVProcessor(
      "Investigation-serials-with-network.csv",
      None,
      "inc-620-cable-one-subs"
    )

    val cableNetworks =
      new CSVProcessor("Investigation-users-one-cable-one-networks.csv", None, "inc-620-cable-one-subs")

    val cableSubs = new CSVProcessor(
      "Investigation-users-one-cable-one-subs.csv",
      None,
      "inc-620-cable-one-subs"
    ).getColumns("id", "user_id", "customer_id", "status", "plan_id", "tier", "organization_id")

    val writer = new BasicWriter("raw_results_cable_one.csv", "result/inc-620-cable-one-subs")
    val writerProcessed = new BasicWriter("processed_results_cable_one.csv", "result/inc-620-cable-one-subs")

    val merged = CableOnePartnerIdsWithSerials.joinOnMatchingColumns(CableOneSerialsWithNetworkId, false)(
      "product_serial" -> "serial_number"
    ).joinOnMatchingColumns(cableNetworks, false)("network_id" -> "network_id")
      .joinOnMatchingColumns(cableSubs, false)("user_id" -> "user_id")

    val processedRows = merged.processRows { row =>
      val customerAccountId = row("customer_account_id")
      val partner_account_id = row("partner_account_id")
      val productSerial = row("product_serial")
      val networkId = row("network_id")
      val networkRole = row("network_role")
//      val networkIsBusiness = row("network_customer_type") == "Business"
      val userRole = UserRole.fromString(row("user_role"))
//      val isEndCustomerRole = userRole == NoUserRole
      val isIspRole = userRole.isIsp
      val isBusinessRole = userRole == BusinessOwner
      val userId = row("user_id")
      val userSubId = row("id")
      val planId = row("plan_id")
      val orgId = row("organization_id")
      val tier = row("tier")

      val ineligibilityReason = if (partner_account_id.isEmpty || customerAccountId.isEmpty) {
        Option.empty[IneligibilityReason]
      } else if (networkId.isEmpty)
        Option(IneligibilityReason.NoNetwork)
      else if (networkRole != "network-owner") {
        Option(IneligibilityReason.NoAssociatedUserWithOwnedNetwork)
      } else if (userId.isEmpty) {
        Option(IneligibilityReason.NoUser)
      } else if (isBusinessRole) {
        Option(IneligibilityReason.BusinessOwner)
      } else if (isIspRole) {
        Option(IneligibilityReason.IspRole)
      } else if (userSubId.isEmpty) {
        Option(IneligibilityReason.NoCurrentSubscription)
      } else if (tier == "premium-plus") {
        Option(IneligibilityReason.AlreadyPlus)
      } else if (orgId != "97554") {
        Option(IneligibilityReason.SubscriptionNotCableOneAssociated)
      } else {
        if (planId != "S030201") Option(IneligibilityReason.WrongPlanForSubscription)
        else Option(IneligibilityReason.Eligible)
      }

      ineligibilityReason.map { reason =>
        (
          partner_account_id.toInt,
          s"$partner_account_id,$customerAccountId,$productSerial,$networkId,$networkRole,$userId,$userRole,$userSubId,$planId,$tier,$orgId,$reason",
          reason
        )
      }
    }

    writerProcessed.writeRow(
      "partner_account_id,customer_account_id,product_serial,networkId,network_role,user_id,user_role,user_sub_id,plan_id,tier,org_id,ineligibility_reason"
    )
    processedRows.flatten.map(row => writerProcessed.writeRow(row._2))
    writerProcessed.closeFile()

    writer.writeCSV(merged)
    writer.closeFile()
  }

}
