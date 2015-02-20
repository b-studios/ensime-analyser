import sbt._
import java.io._
import ScoverageSbtPlugin.ScoverageKeys
import scala.util.Try

// NOTE: the following skips the slower tests
// test-only * -- -l SlowTest

organization := "org.ensime"

name := "ensime-analyser"

scalaVersion := "2.11.4"

version := "0.1-SNAPSHOT"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.ensime"                 %% "ensime"               % "0.9.10-SNAPSHOT",
  "org.slf4j"                  %  "jul-to-slf4j"         % "1.7.9",
  "org.slf4j"                  %  "jcl-over-slf4j"       % "1.7.9"
)

// WORKAROUND: https://github.com/typelevel/scala/issues/75
val jdkDir: File = List(
  // manual
  sys.env.get("JDK_HOME"),
  sys.env.get("JAVA_HOME"),
  // osx
  Try("/usr/libexec/java_home".!!.trim).toOption,
  // fallback
  sys.props.get("java.home").map(new File(_).getParent),
  sys.props.get("java.home")
).flatten.filter { n =>
  new File(n + "/lib/tools.jar").exists
}.headOption.map(new File(_)).getOrElse(
  throw new FileNotFoundException(
    """Could not automatically find the JDK/lib/tools.jar.
      |You must explicitly set JDK_HOME or JAVA_HOME.""".stripMargin
  )
)

// epic hack to get the tools.jar JDK dependency
val JavaTools = file(jdkDir + "/lib/tools.jar")

internalDependencyClasspath in Compile += { Attributed.blank(JavaTools) }

internalDependencyClasspath in Test += { Attributed.blank(JavaTools) }

scalacOptions in Compile ++= Seq(
  "-encoding", "UTF-8", "-target:jvm-1.6", "-feature", "-deprecation",
  "-Xfatal-warnings",
  "-language:postfixOps", "-language:implicitConversions"
)

javacOptions in (Compile, compile) ++= Seq (
  "-source", "1.6", "-target", "1.6", "-Xlint:all", //"-Werror",
  "-Xlint:-options", "-Xlint:-path", "-Xlint:-processing"
)

javacOptions in doc ++= Seq("-source", "1.6")

maxErrors := 1

fork := true

//tests should be isolated, but let's keep an eye on stability
//parallelExecution in Test := false

// passes locations of example jars to the tests
def jars(cp: Classpath): String = {
  for {
    att <- cp
    file = att.data
    if file.isFile & file.getName.endsWith(".jar")
  } yield file.getAbsolutePath
}.mkString(",")

// passes the location of ENSIME's class dirs to the tests
def classDirs(cp: Classpath): String = {
  for {
    att <- cp
    file = att.data
    if file.isDirectory
  } yield file.getAbsolutePath
}.mkString(",")

javaOptions ++= Seq("-XX:MaxPermSize=256m", "-Xmx2g", "-XX:+UseConcMarkSweepGC")

// 0.13.7 introduced awesomely fast resolution caching
updateOptions := updateOptions.value.withCachedResolution(true)

javaOptions in Test ++= Seq(
  "-XX:MaxPermSize=256m", "-Xmx4g", "-XX:+UseConcMarkSweepGC",
  "-Densime.compile.jars=" + jars((fullClasspath in Compile).value),
  "-Densime.test.jars=" + jars((fullClasspath in Test).value),
  "-Densime.compile.classDirs=" + classDirs((fullClasspath in Compile).value),
  "-Densime.test.classDirs=" + classDirs((fullClasspath in Test).value),
  "-Dscala.version=" + scalaVersion.value,
  // sorry! this puts a source/javadoc dependency on running our tests
  "-Densime.jars.sources=" + (updateClassifiers in Test).value.select(
    artifact = artifactFilter(classifier = "sources")
  ).mkString(",")
)

// full stacktraces in scalatest
//testOptions in Test += Tests.Argument("-oF")

scalariformSettings

// let's bump this every time we get more tests
ScoverageKeys.coverageMinimum := 1

ScoverageKeys.coverageFailOnMinimum := true 

licenses := Seq("BSD 3 Clause" -> url("http://opensource.org/licenses/BSD-3-Clause"))

homepage := Some(url("http://github.com/ensime/ensime-analyser"))

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.contains("SNAP")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(
  "Sonatype Nexus Repository Manager", "oss.sonatype.org",
  sys.env.get("SONATYPE_USERNAME").getOrElse(""),
  sys.env.get("SONATYPE_PASSWORD").getOrElse("")
)
