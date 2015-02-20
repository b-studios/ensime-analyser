package org.ensime.analyser

import java.io.File

import akka.actor.ActorSystem
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.ensime.EnsimeApi
import org.ensime.config.{Environment, EnsimeConfig}
import org.ensime.core.Project
import org.ensime.model.{BasicTypeInfo, PackageInfo}
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

/**
 * Entry point for an example analysis tool.
 * Use
 */
object AnalysisMain {
  SLF4JBridgeHandler.removeHandlersForRootLogger()
  SLF4JBridgeHandler.install()

  val log = LoggerFactory.getLogger("AnalysisMain")

  def main(args: Array[String]): Unit = {
    if(args.length < 1 && args.length > 2)
      throw new RuntimeException("Usage: AnalysisMain <.ensime_path> <opt:base_package>")

    val ensimeFile = new File(args(0))
    if (!ensimeFile.exists() || !ensimeFile.isFile)
      throw new RuntimeException(s".ensime file ($ensimeFile) not found")

    val basePackage = if(args.length == 2) args(1) else ""

    val config = EnsimeConfig.parse(Files.toString(ensimeFile, Charsets.UTF_8))

    val api = startEnsime(config)

    try {
      runAnalysis(api, basePackage)
    } finally {
      api.rpcShutdownServer()
    }
  }

  def startEnsime(config: EnsimeConfig): EnsimeApi = {
    // the config file parsing will attempt to create directories that are expected
    require(config.cacheDir.isDirectory, "" + config.cacheDir + " is not a valid cache directory")

    val actorSystem = ActorSystem.create()
    log.info(Environment.info)

    val project = new Project(config, actorSystem)
    project.initProject()
    // sleep to allow project time to initialise and we do not  have a clean way to wait for it to be ready right now.
    Thread.sleep(20000)
    project
  }

  def runAnalysis(implicit api: EnsimeApi, basePackage: String): Unit = {
    val rootPackage: PackageInfo = api.rpcInspectPackageByPath(basePackage).get

    println("\n\n\n\n\n\n")
    dumpPackage(rootPackage)
    println("\n\n\n\n\n\n")
  }

  def dumpPackage(p: PackageInfo, spacer: String = "  ")(implicit api: EnsimeApi): Unit = {
    println("P" + spacer + p.fullName)

    val subTypes = p.members.filter(_.isInstanceOf[BasicTypeInfo])
    subTypes.foreach { st =>
      dumpType(st.asInstanceOf[BasicTypeInfo], spacer)
    }
    val subPackages = p.members.filter(_.isInstanceOf[PackageInfo])

    subPackages.foreach { sp =>
      dumpPackage(sp.asInstanceOf[PackageInfo], spacer + "  ")
    }
  }

  def dumpType(b: BasicTypeInfo, spacer: String = "  ")(implicit api: EnsimeApi): Unit = {
    val typeInfo = api.rpcInspectTypeById(b.typeId).get
    println(s"C$spacer${b.fullName} (${typeInfo.`type`.pos})")
    if(typeInfo.`type`.typeArgs.nonEmpty)
      println(s" $spacer typeArgs: ${b.typeArgs}")
    typeInfo.`type`.members.foreach { m =>
      println(s" $spacer  $m")
    }
  }
}
