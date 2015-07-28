name := "plush"

version := "1.1-SNAPSHOT"

libraryDependencies := Seq(
  "net.debasishg" %% "redisclient" % "2.10",
  "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
  "org.scalatestplus" %% "play" % "1.0.0" % "test"
)

scalacOptions := Seq("-feature")

playScalaSettings
