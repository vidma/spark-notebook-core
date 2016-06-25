package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.io.File

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.commons.io.FileUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import notebook.{GenericFile, Notebook, NotebookResource}
import org.scalatest.time.{Millis, Seconds, Span}

class NotebookProviderTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  val rootPath = Files.createTempDirectory("notebook-provider-test")
  val notebookPath = rootPath.resolve("notebook")
  val emptyPath = rootPath.resolve("empty")
  val notebookFile = "nb.snb"
  val resourceFile = "res.res"

  override def beforeAll() : Unit = {
    Seq(notebookPath, emptyPath).foreach(path => Files.createDirectory(path))
    Seq(notebookFile, resourceFile).foreach{file =>
      val filePath = notebookPath.resolve(file)
      Files.write(filePath, "text".getBytes(StandardCharsets.UTF_8))
    }
  }

  override def afterAll() : Unit = {
    FileUtils.deleteDirectory(rootPath.toFile)
  }

  class BaseProvider extends NotebookProvider {
    override def root: Path = rootPath
    override def get(path: Path) (implicit ev:ExecutionContext): Future[Notebook] = ???
    override def delete(path: Path) (implicit ev:ExecutionContext): Future[Notebook] = ???
    override def save(path: Path, notebook: Notebook) (implicit ev:ExecutionContext): Future[Notebook] = ???
  }

  val T = true
  val F = false

  val providerDefault = new BaseProvider()

  val providerNoFilter = new BaseProvider(){
    override val listingPolicy = (f:File) => true
  }

  val providerExplicitFilter = new BaseProvider() {
    override val listingPolicy = (f:File) => (f.getName endsWith ".snb") || (f.getName startsWith "res")
  }

  val providerPatternFilter = new BaseProvider() {
    override val listingPolicy = (f:File) =>
      if (f.isDirectory) {
        !f.getName.startsWith(".")
      } else {
        f.getName.endsWith(".snb")
    }
  }

  "Notebook provider" should {

    val defaultInstance = new BaseProvider()

    "retrieve a list of resources from a path" in {
      whenReady(defaultInstance.list(notebookPath) ) { files =>
        files.size should be (2)
        files.foreach{
          case nb: NotebookResource => nb.name should be (notebookFile.dropRight(".snb".size))
          case res: GenericFile => res.name should be (resourceFile)
          case x => fail("unexpected resource" + x)
        }
      }
    }

    "retrieve a list of resources from an empty path" in {
      whenReady(defaultInstance.list(emptyPath) ) { files =>
        files should be ('empty)
      }
    }

  }

}
