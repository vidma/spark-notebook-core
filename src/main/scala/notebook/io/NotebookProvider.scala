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

  def verifyProvider(): Future[Unit] = Future.successful(())

  def root: Path

  def listingPolicy: File => Boolean = NotebookProvider.DefaultListingPolicy

  def delete(path: Path)(implicit ev: ExecutionContext): Future[Notebook]

  def get(path: Path)(implicit ev: ExecutionContext): Future[Notebook]

  def save(path: Path, notebook: Notebook)(implicit ev: ExecutionContext): Future[Notebook]

  def list(path: Path)(implicit ev: ExecutionContext): Future[List[Resource]] = {
    val lengthToRoot = root.toFile.getAbsolutePath.length
    def dropRoot(f: java.io.File) = f.getAbsolutePath.drop(lengthToRoot).dropWhile(_ == '/')

    Future {
      val ps: List[java.io.File] = Option(root.resolve(path).toFile.listFiles)
        .filter(_.length != 0) //toList fails if listFils is empty
        .map(_.toList)
        .getOrElse(Nil)
      ps.map { f =>
        val n = f.getName
        if (f.isFile && n.endsWith(".snb")) {
          NotebookResource(n.dropRight(".snb".length), dropRoot(f))
        } else if (f.isFile) {
          GenericFile(n, dropRoot(f), "file")
        } else {
          Repository(n, dropRoot(f))
        }
      }
    }
  }

}


object NotebookProvider {
  val isNotebookFile: File => Boolean = f => f.isFile && f.getName.endsWith(".snb")
  val isVisibleDirectory: File => Boolean = f => f.isDirectory && !f.getName.startsWith(".")
  val DefaultListingPolicy = (f:File) => isNotebookFile(f) || isVisibleDirectory(f)
}

