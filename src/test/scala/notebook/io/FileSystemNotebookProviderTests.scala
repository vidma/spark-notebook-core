package notebook.io

import java.nio.file.{Files, Path, Paths}
import java.util.TimeZone

import com.typesafe.config.ConfigFactory
import notebook.NBSerializer.Metadata
import notebook.Notebook
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}

import scala.collection.JavaConverters._

class FileSystemNotebookProviderTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  var notebook: Notebook = _
  var temp: Path = _
  var provider: NotebookProvider = _
  var target: Path = _

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
    user_save_timestamp =  new DateTime(1999, 9, 9, 9, 9, 9,DateTimeZone.forID("CET")).toDate,
    auto_save_timestamp =  new DateTime(2001, 1, 1, 0, 0, 0,DateTimeZone.forID("CET")).toDate,
    sparkNotebook = Some(sparkNotebook),
    customLocalRepo = customLocalRepo,
    customRepos = customRepos,
    customDeps = customDeps,
    customImports = customImports,
    customArgs = customArgs,
    customSparkConf = customSparkConf)

  val raw =
    """{
      |"metadata" : {
      |  "name" : "test-notebook-name",
      |  "user_save_timestamp" : "1999-09-09T09:09:09.000Z",
      |  "auto_save_timestamp" : "2001-01-01T00:00:00.000Z",
      |  "language_info" : {
      |    "name" : "scala",
      |    "file_extension" : "scala",
      |    "codemirror_mode" : "text/x-scala"
      |  },
      |  "trusted" : true,
      |  "sparkNotebook" : {
      |    "build" : "unit-tests"
      |  },
      |  "customLocalRepo" : "local-repo",
      |  "customRepos" : [ "custom-repo" ],
      |  "customDeps" : [ "\"org.custom\" % \"dependency\" % \"1.0.0\"" ],
      |  "customImports" : [ "import org.cusom.dependency.SomeClass" ],
      |  "customArgs" : [ ],
      |  "customSparkConf" : {
      |    "spark.driverPort" : 1234
      |  }
      |},
      |"cells" : [ ]
      |}
    """.stripMargin

  override def beforeAll: Unit = {
    notebook = Notebook(metadata = Some(metadata), nbformat = None, rawContent = Some(raw))

    temp = Files.createTempDirectory("file-system-notebook-provider")
    val notebookDir = temp.toAbsolutePath.toFile.getAbsolutePath + "/notebook"
    val dirConfig = ConfigFactory.parseMap(Map("notebook.dir" -> notebookDir).asJava)
    val configurator = new FileSystemNotebookProviderConfigurator()
    provider = configurator(dirConfig)

    target = temp.resolve(s"$testName.snb")
  }

  override def afterAll: Unit = {
    FileUtils.deleteDirectory( temp.toFile )
  }

  "File system notebook provider" should {

    "create a notebook file" in {
      whenReady( provider.save(target, notebook) ,  timeout(Span(1, Seconds))) { n =>
        n should be (notebook)
      }
    }

    "load created file" in {
      whenReady( provider.get(target), timeout(Span(1, Seconds)) ) { n =>
        n should be (notebook)
      }
    }

    "fail to load an unexisting file" in {
      whenReady( provider.get(Paths.get("/path/to/nowhere")).failed ) { n =>
        n shouldBe a [java.nio.file.NoSuchFileException]
      }
    }

    "delete the file" in {
      whenReady( provider.delete(target) ) { n =>
        n should be (notebook)
      }
    }

    "fail to load deleted file" in {
      whenReady( provider.get(target).failed ) { n =>
        n shouldBe a [java.nio.file.NoSuchFileException]
      }
    }



  }

}
