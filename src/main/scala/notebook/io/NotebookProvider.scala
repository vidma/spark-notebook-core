package notebook.io

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.config.{ConfigFactory, Config}
import notebook.NBSerializer
import notebook.NBSerializer.Notebook

import scala.concurrent.{Promise, Future}
import scala.util.{Try, Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

trait NotebookProvider {
  def withConfiguration(): Config = ConfigFactory.empty()
  def verifyProvider(): Future[Unit] = Future {}
  def delete(path: Path): Future[Option[Notebook]]
  def get(path: Path): Future[Option[Notebook]]
  def save(path: Path, notebook: Notebook): Future[Option[Notebook]]
}

class FileSystemNotebooksProvider extends NotebookProvider {

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

