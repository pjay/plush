import sbtassembly.Plugin._
import AssemblyKeys._

name := "plush"

version := "1.1-SNAPSHOT"

libraryDependencies := Seq(
  "net.debasishg" %% "redisclient" % "2.10"
)

scalacOptions := Seq("-feature")

playScalaSettings

//assemblySettings
seq(assemblySettings: _*)

mainClass in assembly := Some("play.core.server.NettyServer")

test in assembly := {}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }
}

