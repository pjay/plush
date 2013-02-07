import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "plush"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "net.debasishg" %% "redisclient" % "2.10"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      scalacOptions := Seq("-feature")
    )

}
