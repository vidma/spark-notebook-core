package notebook

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import notebook.NBSerializer._

trait Notebook {
  def metadata: Option[Metadata] = None
  def cells: Option[List[Cell]] = Some(Nil)
  def worksheets: Option[List[Worksheet]] = None
  def autosaved: Option[List[Worksheet]] = None
  def nbformat: Option[Int]
  def name: String = metadata.map(_.name).getOrElse("Anonymous")
  def normalizedName: String = {
    val n = name.toLowerCase.replaceAll("[^\\.a-z0-9_-]", "-")
    n.dropWhile(_ == '-').reverse.dropWhile(_ == '-').reverse
  }
  def rawContent: Option[String] = None
  def isFromRaw = rawContent.isDefined
  def updateMetadata(newMeta: Option[Metadata]): Notebook
}


object Notebook {

  private[notebook] case class NotebookImpl( override val metadata: Option[Metadata] = None,
                           override val cells: Option[List[Cell]] = Some(Nil),
                           override val worksheets: Option[List[Worksheet]] = None,
                           override val autosaved: Option[List[Worksheet]] = None,
                           override val nbformat: Option[Int],
                           override val rawContent: Option[String]
                         ) extends Notebook {
    override def updateMetadata(newMeta: Option[Metadata]): Notebook = this.copy(metadata = newMeta)
  }

  def apply(metadata: Option[Metadata] = None,
            cells: Option[List[Cell]] = Some(Nil),
            worksheets: Option[List[Worksheet]] = None,
            autosaved: Option[List[Worksheet]] = None,
            nbformat: Option[Int],
            rawContent: Option[String]
  ): Notebook = {
      NotebookImpl(metadata, cells, worksheets, autosaved, nbformat, rawContent)
  }

  def read(str: String)(implicit ex : ExecutionContext): Future[Notebook] = {
    NBSerializer.fromJson(str).map(nb =>  NotebookImpl(nb.metadata, nb.cells, nb.worksheets, nb.autosaved, nb.nbformat, Some(str)))
  }

  def write(nb: Notebook)(implicit ex : ExecutionContext): Future[String] = {
    val snb = SerNotebook(nb.metadata, nb.cells, nb.worksheets, nb.autosaved, nb.nbformat)
    NBSerializer.toJson(snb)
  }
}

class NotebookDeserializationException(msg: String, cause: Throwable) extends Exception(msg, cause)
class EmptyNotebookException extends Exception ()
class NotebookNotFoundException(location:String) extends Exception(s"Notebook not found at $location")