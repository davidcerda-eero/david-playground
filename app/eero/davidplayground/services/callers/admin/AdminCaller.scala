//package eero.davidplayground.services.callers.admin
//
//import scala.io.StdIn.readLine
//
//
//class AdminCaller(val environment: String) {
//
//  private val baseUrl: String = if (environment.toLowerCase == "prod") {
//    "https://api-prod.admin.e2ro.com"
//  } else {
//    "https://api-stage.admin.e2ro.com"
//  }
//  private val apiToken = readLine("Input live Admin API token:\n")
//  private val adminHeader = "x-admin-token"
//  def get (path: String, headers: Iterable[(String, String)] = Seq("x-admin-token" -> apiToken)): ={
//    requests.get(url = baseUrl + path, headers = headers)
//  }
//  def post ()
//  def put ()
//  def delete ()
//}
