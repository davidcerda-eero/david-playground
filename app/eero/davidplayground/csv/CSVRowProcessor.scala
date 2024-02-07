package eero.davidplayground.csv

import eero.davidplayground.csv.CSVErrors.{ColumnHeaderDoesNotExist, ColumnHeaderSizeNotEqualToValueSize}

sealed trait CSVErrors extends Throwable {
  val message: String = ""
}

object CSVErrors {
  case object ColumnHeaderSizeNotEqualToValueSize extends CSVErrors
  case class ColumnHeaderDoesNotExist(colName: String) extends CSVErrors {
    override val message = s"Provided column name ${colName} was not found"
  }
}
trait CSVRowCollection extends IndexedSeq[CSVRow]{
  private[csv] val headers: Vector[String]
  private[csv] val rows: IndexedSeq[CSVRow]
}

private[csv] class CSVRow(private[csv] val columnHeaders: Vector[String], private[csv] val values: Vector[String]) extends IndexedSeq[String] {
  if (columnHeaders.size != values.size) throw ColumnHeaderSizeNotEqualToValueSize


  def apply(int: Int): String = values(int)


  def apply(header: String): String = {
    val idx = columnHeaders.indexOf(header)
    if (idx == -1) throw ColumnHeaderDoesNotExist(header)
    values(idx)
  }

  override def length: Int = values.length

  def getHeaderString: Unit = columnHeaders.mkString(",")

  def getValueString: Unit = values.mkString(",")

  def equalHeaders(that: CSVRow): Boolean = {
    this.columnHeaders.sameElements(that)
  }
  def equals(that: CSVRow): Boolean = {
    equalHeaders(that) && this.values.sameElements(that.values)
  }
}

class CSVRowProcessor(private[csv] val rows: IndexedSeq[CSVRow]) extends CSVRowCollection {
 override private[csv]
  val headers = rows.headOption.map(_.columnHeaders).getOrElse(Vector.empty)

  def printColumnNames(): Unit = {
    println(headers.mkString(","))
  }
  def printData(): Unit = {
    for (row <- rows) {
      println(row.getValueString)
    }
  }

  override def length: Int = rows.length

  def apply(int: Int): CSVRow = rows(int)

  def processRows[R](f: Vector[String] => R): IndexedSeq[R] = {
    for (row <- rows) yield {
      f(row.values)
    }
  }

  def getColumnIndex(colName: String): Int = {
    val idx = headers.indexOf(colName)
    if (idx == -1) throw ColumnHeaderDoesNotExist(colName)
    idx
  }

  def processSpecificColumns[R](colNames: String*)(f: (Map[String, Int], IndexedSeq[String]) => R): IndexedSeq[R] = {
    val columnIndexes = colNames.map { name =>
      val idx = getColumnIndex(name)
      (name, idx)
    }.toMap
    rows.map(row => f(columnIndexes, row.values))
  }

  def compareColumnNames(csv: CSVRowProcessor): Vector[(String, Int, Int)] = {
    val sharedColumns = for {
      (thisCol, thisNumber) <- headers.zipWithIndex
      (otherCol, otherNumber) <- csv.headers.zipWithIndex
      if thisCol == otherCol
    } yield {
      (thisCol, thisNumber, otherNumber)
    }
    sharedColumns
  }

  def asSelectColumns(colNames: String*): List[String] = {
    colNames
      .map(getColumn(_))
      .filter(_.nonEmpty)
      .foldLeft(Array.fill(rows.length)("")) { (acc, col) =>
        acc.zip(col).map { case (a, b) => s"$a,$b" }.map(_.split(",").filter(_.nonEmpty).mkString(","))
      }
      .toList
  }

  def getColumn(colName: String): Array[String] = {
    val columnNumber = getColumnIndex(colName)
    (for (row <- rows) yield row(columnNumber)).toArray.tail
  }

  def getColumns(columns: String*): Seq[Array[String]] = {
    columns.map(getColumn)
  }

  def getColumnsMap(columns: String*): Map[String, Array[String]] = {
    columns.map(name => (name, getColumn(name))).toMap
  }

  def getColumnByIndex(idx: Int): IndexedSeq[String] = {
    rows.map(_(idx)).toIndexedSeq
  }
//
//  def JoinOnMatchingColumns(that: CSVRowProcessor, columnsToCompare: (String, String)*): CSVRowProcessor = {
//    val (theseColumns, thoseColumns) = columnsToCompare.map { case (thisCol, thatCol) =>
//      (this.getColumnIndex(thisCol), that.getColumnIndex(thatCol))
//    }.unzip
//    val theseRows = for (thisRow <- this.rows) yield theseColumns.map(thisRow(_))
//    val thoseRows = for (thatRow <- that.rows) yield thoseColumns.map(thatRow(_))
//    theseRows.size.compare(thoseRows.size) match {
//      case x if x >= 0 =>
//        theseRows.zipWithIndex.collect {
//          case (value, idx) if thoseRows.toSet(value) => {
//            rows.that.rows(thoseRows.indexOf(value))
//            rows(idx)
//          }
//        }
//      case x if x < 0 =>
//        thoseRows.zipWithIndex.collect { case (value, idx) if thoseRows.toSet(value) => that.rows(idx) }
//    }
//  }
}
