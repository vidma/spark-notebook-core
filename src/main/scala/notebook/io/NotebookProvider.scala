package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.io.{File, FileFilter}

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import notebook.NBSerializer
import notebook.NBSerializer._

trait NotebookProvider {

  import NotebookProvider._

  def initialize(config: Config = ConfigFactory.empty()): Unit = ()
  def verifyProvider(): Future[Unit] = Future {}
  def root: Path
  def listingPolicy: File => Boolean  = NotebookProvider.DefaultListingPolicy
  def delete(path: Path) : Future[Option[Notebook]]
  def get(path: Path) : Future[Option[Notebook]]
  def save(path: Path, notebook: Notebook) :  Future[Option[Notebook]]

  private [io] lazy val listFilter = new FileFilter() {
    override def accept(file:File): Boolean =  listingPolicy(file)
  }

  def list(path: Path) : Future[List[Resource]] = {
    def relativePath(f: java.io.File): String = root.relativize(Paths.get(f.getAbsolutePath)).toString
    Future {
      Option(root.resolve(path).toFile.listFiles(listFilter))
        .filter(_.length > 0) //toList fails if listFils is empty
        .map(_.toList)
        .getOrElse(Nil)
        .map(f => (f.getName,relativePath(f),f))
        .collect {
          case (name, relPath, file) if isNotebookFile(file) =>
            NotebookResource(name.dropRight(".snb".length), relPath)
          case (name, relPath, file)  if (file.isFile) => GenericFile(name, relPath, "file")
          case (name, relPath, file) => Repository(name, relPath)
        }
    }
  }
}

object NotebookProvider {
  val isNotebookFile: File => Boolean = f => f.isFile && f.getName.endsWith(".snb")
  val isVisibleDirectory: File => Boolean = f => f.isDirectory && !f.getName.startsWith(".")
  val DefaultListingPolicy = (f:File) => isNotebookFile(f) || isVisibleDirectory(f)
}


class FileSystemNotebooksProvider extends NotebookProvider {
  private var config: Config = _ //ouch

  override lazy val root = Paths.get(config.getString("notebooks.dir"))

  override def initialize(config: Config): Unit = {
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