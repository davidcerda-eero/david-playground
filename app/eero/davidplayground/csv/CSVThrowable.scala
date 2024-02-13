package eero.davidplayground.csv



sealed trait CSVThrowable{
  implicit def thrown: CSVError
}

sealed abstract class CSVError(msg: String) extends Exception(msg)

case class ColumnHeaderSizeNotEqualToValueSize(columnSize: Int, valueSize: Int) extends CSVThrowable {
  private val message = s"Header number $columnSize does not equal number of values: $valueSize"
  private class ColumnHeaderSizeNotEqualToValueSize(msg: String) extends CSVError(msg)

  implicit def thrown: CSVError = new ColumnHeaderSizeNotEqualToValueSize(message)
}
case class ColumnHeaderDoesNotExist(colName: String) extends CSVThrowable {
  private val message = s"Provided column name $colName was not found"
  private class ColumnHeaderDoesNotExist(msg: String) extends CSVError(msg)

  implicit def thrown: CSVError = new ColumnHeaderDoesNotExist(message)
}
case class CSVProcessorsHaveDifferentNumberOfRows(theseRows: Int, thoseRows: Int) extends CSVThrowable {
  private val message = s"Row number $theseRows does not equal row number: $theseRows"

  private class CSVProcessorsHaveDifferentNumberOfRows(msg: String) extends CSVError(msg)

  implicit def thrown: CSVError = new CSVProcessorsHaveDifferentNumberOfRows(message)
}
