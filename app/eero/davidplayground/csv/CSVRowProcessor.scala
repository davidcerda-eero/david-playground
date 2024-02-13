package eero.davidplayground.csv

trait CSVRowCollection extends IndexedSeq[CSVRow] {
  private[csv] val rows: Vector[CSVRow]
  private[csv] val headers: Map[String, Int]

  override def map[B](f: CSVRow => B): IndexedSeq[B] = rows.map(f)
  override def flatMap[B](f: CSVRow => IterableOnce[B]): IndexedSeq[B] = rows.flatMap(f)
  override def filter(pred: CSVRow => Boolean): IndexedSeq[CSVRow] = rows.filter(pred)
  override def collect[B](pf: PartialFunction[CSVRow, B]): IndexedSeq[B] = rows.collect(pf)
}

protected[csv] class CSVRow(private[csv] val headers: Map[String, Int], private[csv] val values: Vector[String])
  extends IndexedSeq[String] {

  if (headers.size != values.size) {
    throw ColumnHeaderSizeNotEqualToValueSize(headers.size, values.size)
  }

  def apply(int: Int): String = values(int)

  def apply(header: String): String = {
    val idx = indexOf(header)
    if (idx == -1) throw ColumnHeaderDoesNotExist(header)
    values(idx)
  }

  def apply(header: String*): Vector[String] = {
    header.map(this(_)).toVector
  }

  def indexOf(col: String): Int = headers(col)

  override def length: Int = values.length

  override def map[B](f: String => B): IndexedSeq[B] = values.map(f)

  override def flatMap[B](f: String => IterableOnce[B]): IndexedSeq[B] = values.flatMap(f)

  override def filter(pred: String => Boolean): IndexedSeq[String] = values.filter(pred)

// scalastyle:off method.name
  def ++(that: CSVRow): CSVRow = {
    val sharedHeaders = that.headers.collect {
      case (key, _) if this.contains(key) => key
    }.toIndexedSeq

    val modifiedHeaders = that.headers.map {
      case (header, idx) if sharedHeaders.contains(header) => (header + "2", idx + length)
      case (header, idx) => (header, idx + length)
    }
    CSVRow(this.headers ++ modifiedHeaders, this.values ++ that.values)
  }
  // scalastyle:on method.name

  def emptyThis(): CSVRow = CSVRow0Values(headers)

  def getHeaderString(): String = headers.toSeq.sortBy(_._2).map(_._1).mkString(",")

  def getValueString(): String = values.mkString(",")

  override def toString(): String = {
    headers.toSeq.sortBy(_._2).map(_._1).zip(values).map { case (a, b) => s"$a:$b" }.mkString(",")
  }

  def equalHeaders(that: CSVRow): Boolean = {
    this.headers.iterator.sameElements(that)
  }

  def equals(that: CSVRow): Boolean = {
    equalHeaders(that) && this.values.sameElements(that.values)
  }

  def compareValues(that: CSVRow): Boolean = {
    this.values.sorted.sameElements(that.sorted)
  }

  def get(headers: String*): CSVRow = {
    val newHeaders = headers.zipWithIndex.toMap
    val newValues = headers.map(this(_)).toVector
    CSVRow(newHeaders, newValues)
  }

  private def removeHeaders(headers: String*): Map[String, Int] = {
    val calcNewIdx = (removedIdx: Int, oldIdx: Int) => if (oldIdx > removedIdx) oldIdx - 1 else oldIdx
    headers.foldLeft(this.headers) { case (prevHeaders, toRemove) =>
      val removedIdx = prevHeaders(toRemove)
      (prevHeaders - toRemove).map { case (key, oldIdx) =>
        (key, calcNewIdx(removedIdx, oldIdx))
      }
    }
  }

  def remove(headers: String*): CSVRow = {
    val newHeaders = this.removeHeaders(headers: _*)

    val newValues = {
      val indicesReverseOrder = headers.map(indexOf(_)).sorted(Ordering[Int].reverse)
      indicesReverseOrder.foldLeft(values)((remaining, idx) =>
        remaining.patch(idx, Nil, 1)
      )
    }
    CSVRow(newHeaders, newValues)
  }

  def getAndRemove(header: String*): (CSVRow, CSVRow) = (get(header: _*), remove(header: _*))

  def joinOn(
    that: CSVRow,
    onlyOnMatch: Boolean = true,
    emptyThisRow: Boolean = false
  )(headers: (String, String)*): Option[CSVRow] = {
    val (theseHeaders, thoseHeaders) = headers.unzip
    val (targetThis, restThis) = this.getAndRemove(theseHeaders: _*)
    val (targetThose, restThose) = that.getAndRemove(thoseHeaders: _*)

    if (targetThis.compareValues(targetThose)) {
      Option(restThis ++ targetThis ++ restThose)
    } else if (onlyOnMatch) {
      None
    } else if (emptyThisRow) {
      Option(restThis.emptyThis() ++ targetThis ++ restThose)
    } else {
      Option(restThis ++ targetThis ++ restThose.emptyThis())
    }
  }
}

object CSVRow {
  def empty(): CSVRow = {
    CSVRow0
  }

  def apply(headers: Map[String, Int], values: Vector[String]): CSVRow = {
    if (headers.isEmpty && values.isEmpty) { CSVRow0 }
    else if (values.isEmpty) { CSVRow0Values(headers) }
    else { new CSVRow(headers, values) }
  }
}

private[csv] case object CSVRow0 extends CSVRow(Map.empty, Vector.empty)

private[csv] case class CSVRow0Values(override val headers: Map[String, Int])
  extends CSVRow(headers, Vector.fill(headers.size)(""))

class CSVRowProcessor(private[csv] val rows: Vector[CSVRow] = Vector.empty) extends CSVRowCollection {
  override private[csv] val headers: Map[String, Int] = rows.headOption.map(_.headers).getOrElse(Map.empty)
  // scalastyle:off regex
  def printColumnNames(): Unit = {
    println(headers.toSeq.sortBy(_._2).map(_._1).mkString(","))
  }

  def printData(): Unit = {
    for (row <- rows) {
      println(row.getValueString())
    }
  }

  def printRows(): Unit = {
    rows.foreach(println(_))
  }
  // scalastyle:on regex

  def emptyRow(): CSVRow = CSVRow0Values(headers)

  // scalastyle:off method.name
  def ++(that: CSVRowProcessor): CSVRowProcessor = {
    val result = if (this.isEmpty) {
      that
    } else {
      if (this.length != that.length) {
        throw CSVProcessorsHaveDifferentNumberOfRows(this.length, that.length)
      }
      new CSVRowProcessor(this.rows.zip(that).map { case (a, b) => a ++ b })
    }
    result
  }
  // scalastyle:on method.name

  override def length: Int = rows.length

  def apply(idx: Int): CSVRow = rows(idx)

  def apply(indices: Int*): CSVRowProcessor = new CSVRowProcessor(indices.sorted.map(this(_)).toVector)

  def processRows[R](f: CSVRow => R): IndexedSeq[R] = {
    for (row <- rows) yield {
      f(row)
    }
  }

  def processRowsForGivenColumns[R](columns: String*)(f: (CSVRow) => R): IndexedSeq[R] = {
    for (row <- rows) yield {
      val targets = row.get(columns: _*)
      f(targets)
    }
  }

  def indexOf(row: CSVRow): Int = {
    rows.indexOf(row)
  }

  def indexOfValues(row: CSVRow): Int = {
    rows.indexWhere(_.compareValues(row))
  }

  private def getColumnIndex(colName: String): Int = {
    val idx = headers(colName)
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

  @annotation.nowarn
  def compareColumnNames(that: CSVRowProcessor): Vector[(String, Int, Int)] = {
    this.headers.collect { case (key, idx) if that.contains(key) => (key, idx, that.headers(key)) }.toVector
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

  private def getColumn(header: String): CSVRowProcessor = {
    val columnNumber = getColumnIndex(header)
    val columnValues = for {
      row <- rows
    } yield { new CSVRow(Map(header -> 0), Vector(row(columnNumber))) }
    new CSVRowProcessor(columnValues)
  }

  def getColumns(columns: String*): CSVRowProcessor = {
    columns.map(getColumn).foldLeft(CSVRowProcessor.empty) { (prevColumns, nextColumn) => prevColumns ++ nextColumn }
  }

  def removeRows(indices: Int*): CSVRowProcessor = {
    val reversedIndices = indices.sorted(Ordering[Int].reverse)
    val updatedRows = reversedIndices.foldLeft(rows)((remaining, idx) => remaining.patch(idx, Nil, 1))
    new CSVRowProcessor(updatedRows)
  }

  def removeColumns(headers: String*): CSVRowProcessor = {
    new CSVRowProcessor(rows.map(_.remove(headers: _*)))
  }

  def getAndRemoveColumns(headers: String*): (CSVRowProcessor, CSVRowProcessor) = {
    val (target, rest) = rows.map(_.getAndRemove(headers: _*)).unzip
    (new CSVRowProcessor(target), new CSVRowProcessor(rest))
  }

  def joinOnMatchingColumns(
    that: CSVRowProcessor,
    onlyMatches: Boolean = true
  )(headers: (String, String)*): CSVRowProcessor = {
    val newRows = if (onlyMatches) {
      (for {
        thisRow <- this
        thatRow <- that
      } yield thisRow.joinOn(thatRow)(headers: _*)).flatten
    } else {
      val (theseTarget, thoseTarget) = {
        val (theseHeaders, thoseHeaders) = headers.unzip
        (this.getColumns(theseHeaders: _*), that.getColumns(thoseHeaders: _*))
      }
      val (newTheseRows, indicesToRemove) =
        theseTarget.rows.zipWithIndex.foldLeft((IndexedSeq.empty[CSVRow], Seq.empty[Int])) {
          case ((newRows, indicesToRemove), (thisRow, thisIdx)) =>
            val matchingIndices =
              thoseTarget.zipWithIndex.collect { case (thatRow, thatIdx) if thisRow.compareValues(thatRow) => thatIdx }

            val (newRow, updatedIndices) = if (matchingIndices.nonEmpty) {
              val mergedRows = that(matchingIndices: _*).map(row => this(thisIdx).joinOn(row)(headers: _*)).flatten
              (mergedRows, indicesToRemove ++ matchingIndices)

            } else {
              val mergedRow = this(thisIdx).joinOn(that.emptyRow(), false)(headers: _*)
              (mergedRow.toIndexedSeq, indicesToRemove)
            }
            (newRows ++ newRow, updatedIndices)
        }
      val remaining = that.removeRows(indicesToRemove: _*)
      val remainingThoseRows =
        remaining.map(row => this.emptyRow().joinOn(row, false, true)(headers: _*)).flatten
      newTheseRows ++ remainingThoseRows
    }

    new CSVRowProcessor(newRows.toVector)
  }

//  def separateDuplicates(columns: String*): (CSVRowProcessor, CSVRowProcessor) = {}
}

object CSVRowProcessor {
  def empty: CSVRowProcessor = new CSVRowProcessor(Vector.empty)
}
