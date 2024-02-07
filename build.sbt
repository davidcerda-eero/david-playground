name := "davidplayground"

version := s"davidplayground-${version.value}"

scalacOptions += "-Wconf:src=routes/.*:s,src=src_managed/.*:s,src=*.routes:s,any:error"

val requests = "com.lihaoyi" %% "requests" % "0.8.0" // sbt
libraryDependencies += requests
