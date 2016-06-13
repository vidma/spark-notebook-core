package notebook

import notebook.NBSerializer.{Metadata, Notebook}

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}


class NBSerializerTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  val testName = "test-notebook-name"
  val sparkNotebook = Map("build" -> "unit-tests")
  val customLocalRepo = Some("local-repo")
  val customRepos = Some(List("custom-repo"))
  val customDeps = Some(List(""""org.custom" % "dependency" % "1.0.0""""))
  val customImports = Some(List("""import org.cusom.dependency.SomeClass"""))
  val customArgs = Some(List.empty[String])
  val customSparkConf = Some(JsObject( List(("spark.driverPort", JsNumber(1234))) ))

  val notebook = Notebook(
    Some(new Metadata(
      name = testName,
      sparkNotebook = Some(sparkNotebook),
      customLocalRepo = customLocalRepo,
      customRepos = customRepos,
      customDeps = customDeps,
      customImports = customImports,
      customArgs = customArgs,
      customSparkConf = customSparkConf)),
    Some(Nil),
    None,
    None,
    None
  )

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
      |
      |
    """.stripMargin


  "Notebook serializer" should {

    "ser/deserialize a notebook as valid JSON" in {
      val ser = NBSerializer.write(notebook)
      val deser =  NBSerializer.read(ser)
      deser should be ('defined)
      deser.get should be (notebook)
    }

    "de/serialize a notebook as a valid object" in {
      val deser = NBSerializer.read(notebookSer)
      deser should be ('defined)
      val ser = NBSerializer.write(deser.get)
    }



  }



}
