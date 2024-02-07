package eero.davidplayground
import java.time.{Duration, Instant}

final class LongExtended[A <: Long](val _value: A, val now: Instant) {
  def getMinutesInFuture: java.lang.Long = {
    java.lang.Long.valueOf(now.plus(Duration.ofMinutes(_value)).getEpochSecond)
  }
  def getSecondsInFuture: java.lang.Long = {
    java.lang.Long.valueOf(now.plus(Duration.ofSeconds(_value)).getEpochSecond)
  }
  def getDaysInFuture: java.lang.Long = {
    java.lang.Long.valueOf(now.plus(Duration.ofDays(_value)).getEpochSecond)
  }

  def getMinutesInPast: java.lang.Long = {
    java.lang.Long.valueOf(now.minus(Duration.ofMinutes(_value)).getEpochSecond)
  }

  def getSecondsInPast: java.lang.Long = {
    java.lang.Long.valueOf(now.minus(Duration.ofSeconds(_value)).getEpochSecond)
  }

  def getDaysInPast: java.lang.Long = {
    java.lang.Long.valueOf(now.minus(Duration.ofDays(_value)).getEpochSecond)
  }
}

trait LongImplicit {
  val now: Instant
  implicit def wrapLong(long: Long): LongExtended[Long] = new LongExtended[Long](long, now)
  implicit def unwrapLong(id: LongExtended[Long]): Long = id._value
}
