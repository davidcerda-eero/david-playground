package eero.davidplayground.csv

class CSVError(message: String) extends Exception(message)

sealed trait CSVThrowable {
  protected val message: String
  def throwThis(): Unit = {
    throw new CSVError(message)
  }
}

case class ColumnHeaderSizeNotEqualToValueSize(columnSize: Int, valueSize: Int) extends CSVThrowable {
  protected val message = s"Header number $columnSize does not equal number of values: $valueSize"
}
case class ColumnHeaderDoesNotExist(colName: String) extends CSVThrowable {
  protected val message = s"Provided column name $colName was not found"
}
case class CSVProcessorsHaveDifferentNumberOfRows(theseRows: Int, thoseRows: Int) extends CSVThrowable {
  protected val message = s"Row number $theseRows does not equal row number: $theseRows"
}

trait CSVRowCollection extends IndexedSeq[CSVRow] {
  private[csv] val headers: Vector[String]
  private[csv] val rows: IndexedSeq[CSVRow]

  override def map[B](f: CSVRow => B): IndexedSeq[B] = rows.map(f)
  override def flatMap[B](f: CSVRow => IterableOnce[B]): IndexedSeq[B] = rows.flatMap(f)
}

protected[csv] class CSVRow(private[csv] val columnHeaders: Vector[String], private[csv] val values: Vector[String])
  extends IndexedSeq[String] {
  if (columnHeaders.size != values.size)
    ColumnHeaderSizeNotEqualToValueSize(columnHeaders.size, values.size).throwThis()

  def apply(int: Int): String = values(int)

  def apply(header: String): String = {
    val idx = columnHeaders.indexOf(header)
    if (idx == -1) ColumnHeaderDoesNotExist(header).throwThis()
    values(idx)
  }

  override def length: Int = values.length

  override def map[B](f: String => B): IndexedSeq[B] = values.map(f)

  override def flatMap[B](f: String => IterableOnce[B]): IndexedSeq[B] = values.flatMap(f)

  def ++(that: CSVRow): CSVRow = {
    val sharedHeaders = this.columnHeaders.intersect(that.columnHeaders)
    val modifiedHeaders = that.columnHeaders.map {
      case header if sharedHeaders.contains(header) => header + "2"
      case header => header
    }
    new CSVRow(this.columnHeaders ++ modifiedHeaders, this.values ++ that.values)
  }

  def getHeaderString(): Unit = columnHeaders.mkString(",")

  def getValueString(): Unit = values.mkString(",")

  def equalHeaders(that: CSVRow): Boolean = {
    this.columnHeaders.sameElements(that)
  }
  def equals(that: CSVRow): Boolean = {
    equalHeaders(that) && this.values.sameElements(that.values)
  }
}

class CSVRowProcessor(private[csv] val rows: IndexedSeq[CSVRow] = IndexedSeq.empty) extends CSVRowCollection {
  override private[csv] val headers = rows.headOption.map(_.columnHeaders).getOrElse(Vector.empty)

  def printColumnNames(): Unit = {
    println(headers.mkString(","))
  }

  def printData(): Unit = {
    for (row <- rows) {
      println(row.getValueString())
    }
  }

  def ++(that: CSVRowProcessor): CSVRowProcessor = {
    val result = if (this.isEmpty) that
    else {
      if (this.length != that.length) CSVProcessorsHaveDifferentNumberOfRows(this.length, that.length).throwThis()
      new CSVRowProcessor(this.rows.zip(that).map { case (a, b) => a ++ b })
    }
    result
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
    if (idx == -1) ColumnHeaderDoesNotExist(colName).throwThis()
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

  def getColumn(colName: String): CSVRowProcessor = {
    val columnNumber = getColumnIndex(colName)
    val columnValues = for {
      row <- rows
    } yield { new CSVRow(Vector(colName), Vector(row(columnNumber))) }
    new CSVRowProcessor(columnValues)
  }

  def getColumns(columns: String*): CSVRowProcessor = {
    columns.map(getColumn).foldLeft(CSVRowProcessor.empty) { (prevColumns, nextColumn) => prevColumns ++ nextColumn }
  }

  def getColumnsMap(columns: String*): Map[String, CSVRowProcessor] = {
    columns.map(name => (name, getColumn(name))).toMap
  }

  def getColumnByIndex(idx: Int): CSVRowProcessor = {
    new CSVRowProcessor(rows.map(row => new CSVRow(Vector(headers(idx)), Vector(row(idx)))))
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
object CSVRowProcessor {
  def empty: CSVRowProcessor = new CSVRowProcessor(IndexedSeq.empty)
}
