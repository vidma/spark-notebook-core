package notebook.io


import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryNotEmptyException, Files, Path, Paths}
import java.io.{File, FileFilter, IOException}

import com.typesafe.config.{Config, ConfigFactory}
import notebook.NBSerializer.Metadata
import notebook._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

trait NotebookProvider {

  import NotebookProvider._

  def verifyProvider(): Future[Unit] = Future.successful(())

  def root: Path

  def listingPolicy: File => Boolean = NotebookProvider.DefaultListingPolicy

  def delete(path: Path)(implicit ev: ExecutionContext): Future[Notebook]

  def get(path: Path, version: Option[Version] = None)(implicit ev: ExecutionContext): Future[Notebook]

  def save(path: Path, notebook: Notebook, saveSpec: Option[String] = None)(implicit ev: ExecutionContext): Future[Notebook]

  // Moves the notebook with name notebookName at src Path to the dest Path
  final def move(src: Path, dest: Path)(implicit ev: ExecutionContext): Future[Path] = {
    val srcName = src.getFileName.toString
    val destName = dest.getFileName.toString
    for {
      moved <- moveInternal(src, dest)
      res <- if (srcName == destName) {
          Future.successful(dest)
        } else {
        renameInternal(moved, destName)
      }
    } yield res
  }

  // physically moves the notebook, following the logic inherent to the provider
  def moveInternal(src:Path, dest: Path)(implicit ev: ExecutionContext): Future[Path]

  // Renames the internal data of a notebook
  final def renameInternal(path: Path, newName: String)(implicit ev: ExecutionContext): Future[Path] = {
    val now = new java.util.Date()
    for {
      nb <- get(path)
      meta = nb.metadata.map(_.copy(name = newName, user_save_timestamp = now))
        .orElse(Some(new Metadata(java.util.UUID.randomUUID.toString, newName, now, now)))
      renamedNb = nb.updateMetadata(meta)
      _ <- save(path, renamedNb)
    } yield (path)
  }

  // retrieves available versions of the provided notebook path. To be extended by providers that support versioning.
  def versions(path:Path)(implicit ev: ExecutionContext): Future[List[Version]] = Future.successful(Nil)

  private [io] lazy val listFilter = new FileFilter() {
    override def accept(file:File): Boolean =  listingPolicy(file)
  }

  def list(path: Path)(implicit ec: ExecutionContext): Future[List[Resource]] = {
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

case class Version (id: String, message: String, timestamp: Long)


object NotebookProvider {
  val isNotebookFile: File => Boolean = f => f.isFile && f.getName.endsWith(".snb")
  val isVisibleDirectory: File => Boolean = f => f.isDirectory && !f.getName.startsWith(".")
  val DefaultListingPolicy = (f:File) => isNotebookFile(f) || isVisibleDirectory(f)
}


