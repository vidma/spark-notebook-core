package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.{ConfigFactory, Config}

import scala.concurrent.{Promise, Future}
import scala.util.{Try, Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

import notebook.NBSerializer
import notebook.NBSerializer._

trait NotebookProvider {
  def initialize(config: Config = ConfigFactory.empty()): Unit = ()
  def verifyProvider(): Future[Unit] = Future {}
  def root:Path
  def delete(path: Path): Future[Option[Notebook]]
  def get(path: Path): Future[Option[Notebook]]
  def save(path: Path, notebook: Notebook): Future[Option[Notebook]]
  def list(path: Path): Future[List[Resource]] = {

    val lengthToRoot = root.toFile.getAbsolutePath.length
    def dropRoot(f: java.io.File) = f.getAbsolutePath.drop(lengthToRoot).dropWhile(_ == '/')

    Future {
      path.toFile.listFiles.toList.map { f =>
        val n = f.getName
        if (f.isFile && n.endsWith(".snb")) {
          NotebookResource(
            n.dropRight(".snb".length), dropRoot(f)
          )
        } else if (f.isFile) {
          GenericFile(
            n, dropRoot(f), "file"
          )
        } else {
          Repository(
            n, dropRoot(f)
          )
        }
      }
    }
  }
}

class FileSystemNotebooksProvider extends NotebookProvider {
  var config:Config = null //aouch

  override val root = Paths.get(config.getString("notebooks.dir"))

  override def initialize(config: Config = ConfigFactory.empty()): Unit ={
    this.config = config
  }

  override def delete(path: Path): Future[Option[Notebook]] = {
    val p = Promise[Option[Notebook]]()
    get(path).onComplete {
      case Success(Some(notebook)) =>
        val deleted = Files.deleteIfExists(path)
        p.completeWith(Future{Some(notebook)})
      case Success(None) => p.completeWith(Future{None})
      case Failure(ex) => p.failure(ex)
    }
    p.future
  }

  override def get(path: Path): Future[Option[Notebook]] = {
    Future {
      if (Files.exists(path)) {
        NBSerializer.read(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
      } else {
        None
      }
    }
  }

  def save(path: Path, notebook: Notebook): Future[Option[Notebook]] = {
    Try {
      Files.write(path, NBSerializer.write(notebook).getBytes(StandardCharsets.UTF_8))
      get(path)
    }.getOrElse( Future{ Some(notebook) } )
  }

}

