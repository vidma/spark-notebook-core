package notebook

import java.util.Date

import notebook.NBSerializer.Metadata
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}

import scala.concurrent.ExecutionContext.Implicits.global


class NBSerializerTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  val testName = "test-notebook-name"
  val sparkNotebook = Map("build" -> "unit-tests")
  val customLocalRepo = Some("local-repo")
  val customRepos = Some(List("custom-repo"))
  val customDeps = Some(List(""""org.custom" % "dependency" % "1.0.0""""))
  val customImports = Some(List("""import org.cusom.dependency.SomeClass"""))
  val customArgs = Some(List.empty[String])
  val customSparkConf = Some(JsObject( List(("spark.driverPort", JsNumber(1234))) ))

  val metadata = new Metadata(
    name = testName,
    user_save_timestamp =  new DateTime(1999, 9, 9, 9, 9, 9, DateTimeZone.forID("CET") ).toDate,
    auto_save_timestamp =  new DateTime(2001, 1, 1, 0, 0, 0, DateTimeZone.forID("CET")).toDate,
    sparkNotebook = Some(sparkNotebook),
    customLocalRepo = customLocalRepo,
    customRepos = customRepos,
    customDeps = customDeps,
    customImports = customImports,
    customArgs = customArgs,
    customSparkConf = customSparkConf)

  val notebookSer =
    """{
      |  "metadata" : {
      |    "name" : "test-notebook-name",
      |    "user_save_timestamp" : "1999-09-09T09:09:09.000Z",
      |    "auto_save_timestamp" : "2001-01-01T00:00:00.000Z",
      |    "language_info" : {
      |      "name" : "scala",
      |      "file_extension" : "scala",
      |      "codemirror_mode" : "text/x-scala"
      |    },
      |    "trusted" : true,
      |    "sparkNotebook" : {
      |      "build" : "unit-tests"
      |    },
      |    "customLocalRepo" : "local-repo",
      |    "customRepos" : [ "custom-repo" ],
      |    "customDeps" : [ "\"org.custom\" % \"dependency\" % \"1.0.0\"" ],
      |    "customImports" : [ "import org.cusom.dependency.SomeClass" ],
      |    "customArgs" : [ ],
      |    "customSparkConf" : {
      |      "spark.driverPort" : 1234
      |    }
      |  },
      |  "cells" : [ ]
      |}
    """.stripMargin

  val notebookWithContent = Notebook(Some(metadata), nbformat = None, rawContent = Some(notebookSer))
  val notebookWithoutContent = Notebook(Some(metadata), nbformat = None, rawContent = None)

  "Notebook" should {

    "serialize a notebook as valid JSON" in {
      val futSeser = Notebook.write(notebookWithContent)
      whenReady(futSeser) { nb =>
        nb should be (notebookSer)
      }
    }

    "deserialize a json encoded notebook as a valid object" in {
      val fdser = Notebook.read(notebookSer)
      whenReady(fdser) { ser =>
        ser should be (notebookWithContent)
      }
    }
  }
}
