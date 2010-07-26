/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.scalate.tool.commands

import java.util.{List => JList, Map => JMap}
import java.util.zip.ZipInputStream
import java.io.{FileInputStream, FileWriter, File, ByteArrayOutputStream}
import org.fusesource.scalate.tool.Command
import org.fusesource.scalate.tool.Scalate._

/**
 * <p>
 * Implements the 'scalate create' sub command.
 * </p>
 */
class Create extends Command {

  def name = "create"
  def summary = "Creates your Scalate project fast to get you scalate-ing!"

  def usage() = {
    info("Usage: scalate create [options] archetype groupId artifactId [version] [packageName]")
    info()
    info("  archetype   : the archetype of project to create.")
    info("  groupId     : the maven group Id of the new project")
    info("  artifactId  : the maven artifact Id of the new project")
    info("  version     : the version of the new project (defaults to 1.0-SNAPSHOT)")
    info("  packageName : the package name of generated scala code")
    info()
    info("Archetypes:")
    info()
    info("  empty       : Creates a basic Scalate module")
    info("  guice       : Creates a Guice based Scalate module")
    info()
    info("Options:")
    info()
    info("  --help      : Show this help screen")
    info()
    info("For more help see http://scalate.fusesource.org/documentation/tool.html")
    info()

  }

  def process(args: List[String]): Int = (new Processor).process(args)

  class Processor {

    val archetypes = Map("empty" -> "scalate-archetype-empty", "guice" -> "scalate-archetype-guice")

    var archetypeGroupId = "org.fusesource.scalate.tooling"
    val userDir = System.getProperty("user.dir", ".")
    val zipEntryPrefix = "archetype-resources/"
    var outputDir = userDir
    var packageName = ""
    var groupId = ""
    var artifactId = ""
    var version = "1.0-SNAPSHOT"
    var archetypeArtifactId = ""
    var name = ""

    def process(args: List[String]): Int = {
      intro()
      args match {
        case "--help" :: the_rest =>
          usage()
          return 0
        case archetypeName :: groupId :: artifactId :: rest =>

          val optArchetype = archetypes.get(archetypeName)
          if (optArchetype.isEmpty) {
            info("Invalid syntax: No such archetype '" + archetypeName + "' possible values are " + archetypeNames)
            usage()
            return -1
          }
          if( rest.length > 2 ) {
            info("Invalid syntax: too many arguments")
            usage()
            return -1
          }

          this.archetypeArtifactId = optArchetype.get
          this.groupId = groupId
          this.artifactId = artifactId
          rest match {
            case x :: y :: Nil =>
              this.version = x
              this.packageName = y
            case x :: Nil =>
              this.version = x
            case _ =>
          }
          return createArchetype()

        case _ =>
          info("Invalid syntax: Not enough arguments specified")
          usage()
          return -1
      }
    }

    def archetypeNames = archetypes.keysIterator.toSeq.sortWith(_ < _).mkString("(", ", ", ")")

    def createArchetype(): Int = {

      // lets try find some files from the archetype...
      val archetypesDir = new File(homeDir + "/archetypes")
      val file = new File(archetypesDir, archetypeArtifactId + ".jar")
      if (!file.exists) {
        info("No such archetype '" + archetypeArtifactId + "' in directory " + archetypesDir)
        return -2
      }
      else {
        outputDir = userDir + "/" + artifactId
        val outputFile = new File(outputDir)
        if (outputFile.exists) {
          info("Cannot create archetype as " + outputFile.getAbsolutePath + " already exists")
          return -2
        } else {
          if (packageName.length == 0) {
            packageName = groupId + "." + artifactId
          }

          info("Creating archetype " + archetypeArtifactId + " using maven groupId: " +
                  groupId + " artifactId: " + artifactId + " version: " + version
                  + " in directory: " + outputDir)

          val zip = new ZipInputStream(new FileInputStream(file))
          try {
            var ok = true
            while (ok) {
              val entry = zip.getNextEntry
              if (entry == null) {
                ok = false
              }
              else {
                val fullName = entry.getName
                if (!entry.isDirectory && fullName.startsWith(zipEntryPrefix)) {
                  name = fullName.substring(zipEntryPrefix.length)
                  val longSize = entry.getSize
                  val size = longSize.toInt
                  debug("processing resource: " + name)
                  val bos = new ByteArrayOutputStream()
                  val buffer = new Array[Byte](64 * 1024)
                  var bytes = 1
                  while (bytes > 0) {
                    bytes = zip.read(buffer)
                    if (bytes > 0) {
                      bos.write(buffer, 0, bytes)
                    }
                  }
                  val text = new String(bos.toByteArray)
                  processResource(text)
                }
                zip.closeEntry
              }
            }

            info()
            info("Done. To run the generated project type:")
            info()
            info("  cd " + artifactId)
            info("  mvn jetty:run")
            info()
            info("For more help see http://scalate.fusesource.org/documentation/")
            info()

          } finally {
            zip.close
          }
          return 0
        }
      }
    }

    protected val webInfResources = "src/main/webapp/WEB-INF/resources"
    protected val sourcePathRegexPattern = "(src/(main|test)/(java|scala)/)(.*)".r.pattern

    protected def processResource(fileContents: String): Unit = {
      val idx = name.lastIndexOf('/')
      val matcher = sourcePathRegexPattern.matcher(name)
      val dirName = if (packageName.length > 0 && idx > 0 && matcher.matches) {
        val prefix = matcher.group(1)
        outputDir + "/" + prefix + packageName.replace('.', '/') + "/" + name.substring(prefix.length)
      }
      else if (packageName.length > 0 && name.startsWith(webInfResources)) {
        outputDir + "/src/main/webapp/WEB-INF/" + packageName.replace('.', '/') + "/resources" + name.substring(webInfResources.length)
      }
      else {
        outputDir + "/" + name
      }

      // lets replace properties...
      val dir = new File(dirName)
      dir.getParentFile.mkdirs
      val out = new FileWriter(dir)
      out.write(transformContents(fileContents))
      out.close
    }

    protected def transformContents(fileContents: String): String = {

      var answer = replaceVariable(fileContents, "package", packageName)

      if (name == "pom.xml") {
        // lets replace groupId and artifactId in pom.xml
        answer = answer.replaceFirst("""<groupId>.*</groupId>""", """<groupId>""" + groupId + """</groupId>""")
        answer = answer.replaceFirst("""<artifactId>.*</artifactId>""", """<artifactId>""" + artifactId + """</artifactId>""")
        answer = answer.replaceFirst("""<version>.*</version>""", """<version>""" + version + """</version>""")
      }
      answer
    }


    protected def replaceVariable(text: String, name: String, value: String): String = {
      text.replaceAll("""([^\\])\$\{""" + name + """\}""", "$1" + value)
    }
  }
}