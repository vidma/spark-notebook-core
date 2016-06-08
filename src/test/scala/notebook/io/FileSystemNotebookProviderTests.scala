package notebook.io

import java.nio.file.{Files, Path}

import com.typesafe.config.ConfigFactory
import notebook.NBSerializer.{Metadata, Notebook}
import org.apache.commons.io.FileUtils
import org.scalatest.concurrent.ScalaFutures
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

  override def beforeAll: Unit = {
    notebook = Notebook(
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
      whenReady( provider.save(target, notebook) ) { n =>
        n shouldBe(Some(notebook))
      }
    }

    "load created file" in {
      whenReady( provider.get(target) ) { n =>
        n shouldBe(Some(notebook))
      }
    }

    "delete the file" in {
      whenReady( provider.delete(target) ) { n =>
        n shouldBe(Some(notebook))
      }
    }

    "fail to load deleted file" in {
      whenReady( provider.get(target) ) { n =>
        n shouldBe(None)
      }
    }

  }

}
