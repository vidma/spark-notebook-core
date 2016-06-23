package notebook.io

import java.nio.file.{Files, Path}

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}
import scala.collection.JavaConverters._

class FileSystemNotebookProviderConfiguratorTests extends WordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  import scala.concurrent.ExecutionContext.Implicits.global

  var notebookDir : String = _
  var tempDir: Path = _

  override def beforeAll: Unit = {
    tempDir = Files.createTempDirectory("file-system-notebook-provider")
    notebookDir = tempDir.toAbsolutePath.toFile.getAbsolutePath + "/notebook"
  }


  "File system notebook provider configurator" should {
    "be instantiable" in {
      noException should be thrownBy (Class.forName("notebook.io.FileSystemNotebookProviderConfigurator").newInstance().asInstanceOf[Configurable[NotebookProvider]])
    }

    "configure a new notebook provider" in {
      val configurator = Class.forName("notebook.io.FileSystemNotebookProviderConfigurator").newInstance().asInstanceOf[Configurable[NotebookProvider]]
      val dirConfig = ConfigFactory.parseMap(Map("notebook.dir" -> notebookDir).asJava)
      val provider = new FileSystemNotebookProviderConfigurator()
      val notebookProvider = provider(dirConfig )
      notebookProvider shouldBe a[NotebookProvider]
    }
  }

}
