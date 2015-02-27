name := "plush"

version := "1.1-SNAPSHOT"

libraryDependencies := Seq(
  "net.debasishg" %% "redisclient" % "2.10",
  "com.notnoop.apns" % "apns" % "1.0.0.Beta6"
)

scalacOptions := Seq("-feature")

playScalaSettings
