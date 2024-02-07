package eero.davidplayground

import java.time.Instant

class MigrationSlots(lastSlot: Option[Long]) {

  val migrationStart1 = Instant.parse("2022-11-15T23:59:00Z").getEpochSecond
  val migrationEnd1 = Instant.parse("2022-11-16T05:00:00Z").getEpochSecond

  val migrationSize = migrationEnd1 - migrationStart1

  var migrationSlots1 = new Array[Boolean]((migrationEnd1 - migrationStart1).toInt)

  lastSlot match {
    case Some(last) =>
      0.until((last - migrationStart1).toInt)
        .foreach(
          slot => migrationSlots1(slot.toInt) = true
        )
    case None => 1
  }

  def choseSlot(): Long = {
    if (migrationSlots1.last == false) {
      val slot = migrationSlots1.indexOf(false)
      migrationSlots1(slot) = true
      migrationStart1 + slot
    } else {
      migrationSlots1 = new Array[Boolean]((migrationEnd1 - migrationStart1).toInt)
      val slot = migrationSlots1.indexOf(false)
      migrationSlots1(slot) = true
      migrationStart1 + slot
    }
  }
}
