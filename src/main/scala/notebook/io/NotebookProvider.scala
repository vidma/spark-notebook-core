package notebook.io


import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryNotEmptyException, Files, Path, Paths}
import java.io.{File, FileFilter, IOException}

import com.typesafe.config.{Config, ConfigFactory}
import notebook._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

trait Configurable[T] {
  def apply(config: Config = ConfigFactory.empty()) : T
}

trait NotebookProvider {

  import NotebookProvider._

  def verifyProvider(): Future[Unit] = Future.successful(())

  def root: Path

  def listingPolicy: File => Boolean = NotebookProvider.DefaultListingPolicy

  def delete(path: Path)(implicit ev: ExecutionContext): Future[Notebook]

  def get(path: Path)(implicit ev: ExecutionContext): Future[Notebook]

  def save(path: Path, notebook: Notebook)(implicit ev: ExecutionContext): Future[Notebook]

  private [io] lazy val listFilter = new FileFilter() {
    override def accept(file:File): Boolean =  listingPolicy(file)
  }

  def list(path: Path)(implicit ev: ExecutionContext) : Future[List[Resource]] = {
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


