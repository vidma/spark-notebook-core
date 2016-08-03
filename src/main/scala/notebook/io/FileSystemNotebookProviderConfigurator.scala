package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.Config
import notebook.{Notebook, NotebookNotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileSystemNotebookProviderConfigurator extends Configurable[NotebookProvider] {

  import FileSystemNotebookProviderConfigurator._

  override def apply(config: Config)(implicit ec:ExecutionContext): Future[NotebookProvider] = {

    val fDir:Future[Path] = Future {
      val dir = config.getString(NotebooksDir)
      Paths.get(dir)
    }.recoverWith{case t:Throwable => Future.failed(new ConfigurationMissingException(NotebooksDir))}
    fDir.map(dir => new FileSystemNotebookProvider(dir))
  }

  private[FileSystemNotebookProviderConfigurator] class FileSystemNotebookProvider(override val root: Path) extends NotebookProvider {

    override def delete(path: Path)(implicit ev: ExecutionContext): Future[Notebook] = {
      get(path).flatMap { notebook =>
        val res: Future[Unit] = try {
          val deleted = Files.deleteIfExists(path)
          if (!deleted) {
            Future.failed(new NotebookNotFoundException(path.toString))
          } else {
            Future.successful(())
          }
        } catch {
          case ex: Throwable => Future.failed(ex)
        }
        res.map(_ => notebook)
      }
    }

    override def get(path: Path, version: Option[Version] = None)(implicit ev: ExecutionContext): Future[Notebook] = {
      Future{Files.readAllBytes(path)}.flatMap(bytes => Notebook.read(new String(bytes, StandardCharsets.UTF_8)))
    }

    override def save(path: Path, notebook: Notebook, saveSpec:Option[String] = None)(implicit ev: ExecutionContext): Future[Notebook] = {
      Notebook.write(notebook).map { nb =>
          Files.write(path, nb.getBytes(StandardCharsets.UTF_8))
      }.map(_ => notebook)
    }
  }
}
object FileSystemNotebookProviderConfigurator {
  val NotebooksDir = "notebooks.dir"
}