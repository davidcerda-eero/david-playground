package eero.davidplayground

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

class MigrationSlotWithLock(migrationStart: Instant) {
  val migrationStartEpoch = migrationStart.getEpochSecond
  val migrationEnd = Instant.parse("2022-11-16T10:00:00Z").getEpochSecond

  val lock = new ReentrantLock()

  var migrationSlots = new Array[Boolean]((migrationEnd - migrationStartEpoch).toInt)

  private def inLock[T](f: => T): T = {
    lock.lock()
    try {
      f
    } finally {
      lock.unlock()
    }
  }

  def choseSlot(): Long = {
    if (migrationSlots.last == false) {
      val slot = inLock {
        val slot = migrationSlots.indexOf(false)
        migrationSlots(slot) = true
        slot
      }
      migrationStartEpoch + slot
    } else {
      val slot = inLock {
        migrationSlots = new Array[Boolean]((migrationEnd - migrationStartEpoch).toInt)
        val slot = migrationSlots.indexOf(false)
        migrationSlots(slot) = true
        slot
      }
      migrationStartEpoch + slot
    }
  }
}
