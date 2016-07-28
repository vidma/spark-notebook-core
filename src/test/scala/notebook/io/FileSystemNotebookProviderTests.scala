package notebook.io

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.TimeZone

import com.typesafe.config.ConfigFactory
import notebook.NBSerializer.Metadata
import notebook.Notebook
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}
import play.libs.Json

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

class FileSystemNotebookProviderTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  var tempPath: Path = _
  var provider: NotebookProvider = _
  var notebookPath: Path = _

  val id = "foo-bar-loo-lar"
  val testName = "test-notebook-name"
  val sparkNotebook = Map("build" -> "unit-tests")
  val customLocalRepo = Some("local-repo")
  val customRepos = Some(List("custom-repo"))
  val customDeps = Some(List(""""org.custom" % "dependency" % "1.0.0""""))
  val customImports = Some(List("""import org.cusom.dependency.SomeClass"""))
  val customArgs = Some(List.empty[String])
  val customSparkConf = Some(JsObject( List(("spark.driverPort", JsNumber(1234))) ))

  val metadata = new Metadata(
    id =  id,
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
      |  "id" : "foo-bar-loo-lar",
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
      |  },
      |  "customVars" : null
      |},
      |"cells" : [ ]
      |}
    """.stripMargin

  val notebook = Notebook(metadata = Some(metadata), nbformat = None, rawContent = Some(raw))

  override def beforeAll: Unit = {
    tempPath = Files.createTempDirectory("file-system-notebook-provider")
    notebookPath = tempPath.resolve("notebooks")
    Files.createDirectories(notebookPath)

    val dirConfig = ConfigFactory.parseMap(Map("notebook.dir" -> notebookPath.toAbsolutePath.toString).asJava)
    val configurator = new FileSystemNotebookProviderConfigurator()
    provider = configurator(dirConfig)
  }

  override def afterAll: Unit = {
    FileUtils.deleteDirectory( tempPath.toFile )
  }

  "File system notebook provider" should {

    "create a notebook file" in {
      val nbPath = notebookPath.resolve("testNew.snb")
      assume(!nbPath.toFile.exists())
      whenReady( provider.save(nbPath, notebook) ) { n =>
        nbPath.toFile.exists() should be (true)
      }
    }

    "created content should be valid JSON" in {
      val nbPath = notebookPath.resolve("testJson.snb")
      whenReady( provider.save(nbPath, notebook) ) { n =>
        val content = Source.fromFile(nbPath.toFile).mkString("")
        Try{Json.parse(content)} should be ('success)
      }
    }

    "load saved file" in {
      val nbPath = notebookPath.resolve("testLoad.snb")
      val loadedNb = for {
        _ <- provider.save(nbPath, notebook)
        loaded <- provider.get(nbPath)
      } yield loaded

      whenReady( loadedNb ) { nb =>
        nb.normalizedName should be (notebook.normalizedName)
        nb.metadata should be (notebook.metadata)
        nb.cells should be (notebook.cells)
        nb.name should be (notebook.name)
        nb.autosaved should be (notebook.autosaved)
        nb.nbformat should be (notebook.nbformat)
        nb.worksheets should be (notebook.worksheets)
        // avoid comparing the raw content b/c it differs in indentation after JSON pretty print
      }
    }

    "fail to load an unexisting file" in {
      whenReady( provider.get(Paths.get("/path/to/nowhere")).failed ) { n =>
        n shouldBe a [java.nio.file.NoSuchFileException]
      }
    }

    "delete the file" in {
      val nbPath = notebookPath.resolve("testDeleted.snb")
      val deletedNb = for {
        _ <- provider.save(nbPath,notebook)
        deleted <- provider.delete(nbPath)
      } yield deleted

      whenReady( deletedNb ) { n =>
        nbPath.toFile.exists() should be (false)
      }
    }

    "fail to load deleted file" in {
      val nbPath = notebookPath.resolve("notThere.snb")
      whenReady( provider.get(nbPath).failed ) { n =>
        n shouldBe a [java.nio.file.NoSuchFileException]
      }
    }

  }

}
