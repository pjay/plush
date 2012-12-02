import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "plush"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "net.debasishg" %% "redisclient" % "2.8"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      
    )

}
