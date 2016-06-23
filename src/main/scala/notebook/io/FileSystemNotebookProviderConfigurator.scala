package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.Config
import notebook.{Notebook, NotebookNotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileSystemNotebookProviderConfigurator extends Configurable[NotebookProvider] {


  override def apply(config: Config): NotebookProvider = new ConfigurableFileSystemNotebook(config)

  private[FileSystemNotebookProviderConfigurator] class ConfigurableFileSystemNotebook(val config: Config) extends NotebookProvider {

    override lazy val root = Paths.get(config.getString("notebooks.dir"))

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

    override def get(path: Path)(implicit ev: ExecutionContext): Future[Notebook] = {
      Future{Files.readAllBytes(path)}.flatMap(bytes => Notebook.read(new String(bytes, StandardCharsets.UTF_8)))
    }

    override def save(path: Path, notebook: Notebook)(implicit ev: ExecutionContext): Future[Notebook] = {
      Notebook.write(notebook).flatMap { nb =>
        Try {
          Files.write(path, nb.getBytes(StandardCharsets.UTF_8))
        } match {
          case Success(_) => Future.successful(notebook)
          case Failure(ex) => Future.failed(ex)
        }
      }
    }
  }
}
