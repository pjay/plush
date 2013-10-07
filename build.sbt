name := "plush"

version := "1.1-SNAPSHOT"

libraryDependencies := Seq(
  "net.debasishg" %% "redisclient" % "2.10"
)

scalacOptions := Seq("-feature")

playScalaSettings
