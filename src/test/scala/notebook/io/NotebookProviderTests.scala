package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.config.ConfigFactory
import notebook.NBSerializer.{GenericFile, Notebook, NotebookResource}
import org.apache.commons.io.FileUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}


import scala.collection.JavaConverters._
import scala.concurrent.Future


class NotebookProviderTests extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

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

  "Notebook provider" should {

    val defaultInstance = new NotebookProvider() {
      override def root: Path = rootPath
      val successfulNothing : Future[Option[Notebook]] = Future.successful(None)
      override def get(path: Path): Future[Option[Notebook]] = successfulNothing
      override def delete(path: Path): Future[Option[Notebook]] = successfulNothing
      override def save(path: Path, notebook: Notebook): Future[Option[Notebook]] = successfulNothing
    }

    "initialize with default values" in {
      noException should be thrownBy defaultInstance.initialize()
    }

    "initialize with a notebook directory" in {
      val notebookDir = rootPath.toAbsolutePath.toFile.getAbsolutePath + "/notebook"
      val dirConfig = ConfigFactory.parseMap(Map("notebook.dir" -> notebookDir).asJava)
      noException should be thrownBy defaultInstance.initialize(dirConfig)
    }

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
