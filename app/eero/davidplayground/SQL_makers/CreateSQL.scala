package eero.davidplayground.SQL_makers

import eero.davidplayground.csv.CSVRowProcessor
import javax.inject.Inject

class CreateSQL @Inject() (rowProcessor: CSVRowProcessor) {
  private def createValueStatementString(values: IndexedSeq[String], valueHeader: String): String = {
    val allButLast = values.dropRight(1).mkString(",\n\t")
    val last = values.lastOption.getOrElse("")
    s"$valueHeader\n\t$allButLast,\n\t$last"
  }

  private def createInsertStatement(tableName: String)(columnNames: String*): String = {
    val base = s"INSERT INTO $tableName "
    val columnStatement = "(" + columnNames.mkString(", ") + ")\n"
    base + columnStatement
  }

  private def createUpdateStatement(tableName: String)(
    targetColumns: Seq[UpdateColumn[_]]
  )(valueStatement: String) = {
    val base = s"UPDATE $tableName\n"
    val updateTableName = s"${tableName}_update"
    val createUpdateColumns =
      (columns: Seq[String], table: String) => columns.map(c => s"$table$c=$updateTableName.$c")

    val setValues = targetColumns.collect {
      case UpdateColumn(name, _, _, SetClause) => name
    }
    val setTargets = createUpdateColumns(setValues, "").mkString(", ")
    val set = s"SET $setTargets, updated = now()\n"
    val as = s"\n) as $updateTableName(${targetColumns.map(_.name).mkString(", ")})\n"
    val whereValues = targetColumns.collect {
      case UpdateColumn(name, _, _, WhereClause) => name
    }
    val where = "WHERE " + createUpdateColumns(whereValues, tableName + ".").mkString("\nAND ")
    base + set + valueStatement + as + where + ";"
  }

  private def createValueStatements(rows: IndexedSeq[Seq[ColumnData[_]]], valueHeader: String): String = {
    val valueStatements = for {
      row <- rows
    } yield {
      val rowStatement = row.map(_.getData).mkString(", ")
      s"($rowStatement)"
    }
    createValueStatementString(valueStatements, valueHeader)
  }

  def createTransaction(sql: String): String = {
    s"BEGIN;\n$sql\n\nCOMMIT;"
  }

  def createInsertSQL(tableName: String)(
    dataColumns: Seq[String],
    insertColumns: Seq[String]
  )(
    transformation: (Map[String, Int], IndexedSeq[String]) => Option[Seq[ColumnData[_]]]
  ): String = {
    val transformedRows = rowProcessor.processSpecificColumns(dataColumns: _*) { case (indexes, row) =>
      transformation(indexes, row)
    }.flatten
    val insertStatement = createInsertStatement(tableName)(insertColumns: _*)
    val valueStatement = createValueStatements(transformedRows, "VALUES")
    insertStatement + valueStatement + ";"
  }

  def createUpdateSQL(tableName: String)(
    targetColumns: Seq[UpdateColumn[_]]
  )(
    transformation: (Map[String, Int], IndexedSeq[String]) => Option[Seq[ColumnData[_]]]
  ): String = {
    val transformedRows = rowProcessor.processSpecificColumns(targetColumns.map(_.name): _*) { case (indexes, row) =>
      transformation(indexes, row)
    }.flatten
    val valueStatement = createValueStatements(transformedRows, "FROM (VALUES")
    createUpdateStatement(tableName)(targetColumns)(valueStatement)
  }
}
//private object ColumnData {
//  private def convertValue[T: WeakTypeTag](columnData: ColumnData[T]): String = {
//    println(weakTypeOf[T])
//    this match {
//      case _ if weakTypeOf[T] <:< weakTypeOf[String] => s"'${columnData.rawData}'"
//      case _ if weakTypeOf[T] <:< weakTypeOf[Int] => columnData.rawData.toString
//      case _ if weakTypeOf[T] <:< weakTypeOf[Option[Instant]] =>
//        columnData.rawData.asInstanceOf[Option[_]].map(_.toString).getOrElse("now()")
//      case _ if weakTypeOf <:< weakTypeOf[Instant] => columnData.rawData.toString
//      case _ => columnData.rawData.toString
//    }
//  }
//}
trait ColumnData[T] {
  val name: String
  val columnType: ColumnType
  val rawData: T
  private val data: String = columnType match {
    case IntColumn => rawData.toString
    case StringColumn => s"'$rawData'"
    case TimeColumn if rawData.isInstanceOf[Option[_]] =>
      rawData.asInstanceOf[Option[_]].map(_.toString).getOrElse("now()")
    case TimeColumn => rawData.toString
  }
  def getData: String = data
}
case class InsertColumn[T](name: String, columnType: ColumnType, rawData: T = "") extends ColumnData[T]
case class UpdateColumn[T](name: String, columnType: ColumnType, rawData: T = "", updateType: UpdateClause = SetClause)
  extends ColumnData[T]

sealed trait ColumnType

case object IntColumn extends ColumnType
case object StringColumn extends ColumnType
case object TimeColumn extends ColumnType

sealed trait UpdateClause

case object SetClause extends UpdateClause
case object WhereClause extends UpdateClause
