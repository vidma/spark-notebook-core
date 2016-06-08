package notebook.io

import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import notebook.NBSerializer._

trait Configurable[T] {
  def apply(config: Config = ConfigFactory.empty()) : T
}

trait NotebookProvider {

  def verifyProvider(): Future[Unit] = Future {}
  def root: Path
  def delete(path: Path)(implicit ev:ExecutionContext) : Future[Option[Notebook]]
  def get(path: Path)(implicit ev:ExecutionContext) : Future[Option[Notebook]]
  def save(path: Path, notebook: Notebook)(implicit ev:ExecutionContext) :  Future[Option[Notebook]]
  def list(path: Path)(implicit ev:ExecutionContext) : Future[List[Resource]] = {
    val lengthToRoot = root.toFile.getAbsolutePath.length
    def dropRoot(f: java.io.File) = f.getAbsolutePath.drop(lengthToRoot).dropWhile(_ == '/')

    Future {
      val ps:List[java.io.File] = Option(root.resolve(path).toFile.listFiles)
                                          .filter(_.length != 0) //toList fails if listFils is empty
                                          .map(_.toList)
                                          .getOrElse(Nil)
      ps.map { f =>
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

