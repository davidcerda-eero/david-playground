package eero.davidplayground.csv

object PathHelper {
  def standardizePath(elements: String*): String = {
    val dir = elements.dropRight(1).foldLeft("")((elem1, elem2) => "/" + elem1 + "/" + elem2 + "/")
    val fileName = elements.lastOption.getOrElse("")
    val fullPath = dir + fileName
    val badPattern = "/+".r
    badPattern.replaceAllIn(fullPath, "/")
  }
}
