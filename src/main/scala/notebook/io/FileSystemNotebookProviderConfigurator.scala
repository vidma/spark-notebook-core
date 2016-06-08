package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.Config
import notebook.NBSerializer
import notebook.NBSerializer.Notebook

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class FileSystemNotebookProviderConfigurator extends Configurable[NotebookProvider] {


  override def apply(config: Config): NotebookProvider = new ConfigurableFileSystemNotebook(config)

  private[FileSystemNotebookProviderConfigurator] class ConfigurableFileSystemNotebook(val config:Config) extends NotebookProvider {

    override lazy val root = Paths.get(config.getString("notebooks.dir"))

    override def delete(path: Path)(implicit ec: ExecutionContext) : Future[Option[Notebook]] = {
      get(path).map   {
        case Some(notebook) =>
          val deleted = Files.deleteIfExists(path)
          Some(notebook)
        case x => x
      }
    }

    override def get(path: Path)(implicit ec: ExecutionContext): Future[Option[Notebook]] = {
      Future {
        if (Files.exists(path)) {
          NBSerializer.read(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
        } else {
          None
        }
      }
    }

    def save(path: Path, notebook: Notebook)(implicit ec: ExecutionContext): Future[Option[Notebook]] = {
      Try {
        Files.write(path, NBSerializer.write(notebook).getBytes(StandardCharsets.UTF_8))
        get(path)
      }.getOrElse(Future {
        Some(notebook)
      })
    }
  }
}
