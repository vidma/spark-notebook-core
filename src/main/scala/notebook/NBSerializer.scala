package notebook

import java.util.Date

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object NBSerializer {

  trait Output {
    def output_type: String
  }

  case class ScalaOutput(name: String,
                         override val output_type: String,
                         prompt_number: Int,
                         html: Option[String],
                         text: Option[String]
                        ) extends Output

  implicit val scalaOutputFormat = Json.format[ScalaOutput]

  case class ExecuteResultMetadata(id: Option[String] = None)

  implicit val executeResultMetadataFormat = Json.format[ExecuteResultMetadata]

  case class ScalaExecuteResult(metadata: ExecuteResultMetadata,
                                data: Map[String, String],
                                data_list: Option[Map[String, List[String]]],
                                output_type: String,
                                execution_count: Int
                               ) extends Output

  implicit val scalaExecuteResultFormat = Json.format[ScalaExecuteResult]

  case class PyError(name: String,
                     override val output_type: String,
                     prompt_number: Int,
                     traceback: String
                    ) extends Output

  implicit val pyErrorFormat = Json.format[PyError]

  case class ScalaError(ename: String,
                        override val output_type: String,
                        traceback: List[String]
                       ) extends Output

  implicit val scalaErrorFormat = Json.format[ScalaError]

  case class ScalaStream(name: String,
                         override val output_type: String,
                         text: String) extends Output

  implicit val scalaStreamFormat = Json.format[ScalaStream]

  implicit val outputReads: Reads[Output] = Reads { (js: JsValue) =>
    val tpe = (js \ "output_type").as[String]
    tpe match {
      case "execute_result" => scalaExecuteResultFormat.reads(js)
      case "stout" => scalaOutputFormat.reads(js)
      case "pyerr"  => pyErrorFormat.reads(js)
      case "error" => scalaErrorFormat.reads(js)
      case "stream" => scalaStreamFormat.reads(js)
      case x =>
        throw new IllegalStateException("Cannot read this output_type: " + x)
    }
  }

  implicit val outputWrites: Writes[Output] = Writes {
    case o: ScalaExecuteResult => scalaExecuteResultFormat.writes(o)
    case o: ScalaOutput => scalaOutputFormat.writes(o)
    case o: ScalaError => scalaErrorFormat.writes(o)
    case o: ScalaStream => scalaStreamFormat.writes(o)
    case x =>
      throw new IllegalStateException("Cannot read this output_type: " + x)
  }

  implicit val outputFormat: Format[Output] = Format(outputReads, outputWrites)

  case class CellMetadata(
                           trusted: Option[Boolean],
                           input_collapsed: Option[Boolean],
                           output_stream_collapsed: Option[Boolean],
                           collapsed: Option[Boolean],
                           presentation: Option[JsObject],
                           id: Option[String]=None,
                           extra:Option[JsObject] = None
                         )

  implicit val codeCellMetadataFormat = Json.format[CellMetadata]

  trait Cell {
    def metadata: CellMetadata

    def cell_type: String
  }

  case class CodeCell(
                       metadata: CellMetadata,
                       cell_type: String,
                       source: String,
                       language: Option[String],
                       prompt_number: Option[Int] = None,
                       output: Option[JsObject] = None,
                       outputs: Option[List[Output]] = None
                     ) extends Cell

  implicit val codeCellFormat = Json.format[CodeCell]

  case class MarkdownCell(
                           metadata: CellMetadata,
                           cell_type: String = "markdown",
                           source: String
                         ) extends Cell

  implicit val markdownCellFormat = Json.format[MarkdownCell]

  case class RawCell(metadata: CellMetadata, cell_type: String = "raw", source: String) extends Cell

  implicit val rawCellFormat = Json.format[RawCell]

  case class HeadingCell(
                          metadata: CellMetadata,
                          cell_type: String = "heading",
                          source: String,
                          level: Int
                        ) extends Cell

  implicit val headingCellFormat = Json.format[HeadingCell]

  case class LanguageInfo(name: String, file_extension: String, codemirror_mode: String)

  implicit val languageInfoFormat: Format[LanguageInfo] = Json.format[LanguageInfo]
  val scala: LanguageInfo = LanguageInfo("scala", "scala", "text/x-scala")

  case class Metadata(
                       id: String,
                       name: String,
                       user_save_timestamp: Date,
                       auto_save_timestamp: Date,
                       language_info: LanguageInfo = scala,
                       trusted: Boolean = true,
                       sparkNotebook:Option[Map[String, String]] = None,
                       customLocalRepo: Option[String] = None,
                       customRepos: Option[List[String]] = None,
                       customDeps: Option[List[String]] = None,
                       customImports: Option[List[String]] = None,
                       customArgs: Option[List[String]] = None,
                       customSparkConf: Option[JsObject] = None,
                       customVars: Option[Map[String, String]] = None
                     )

  implicit val metadataFormat: Format[Metadata] = {
    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val r: Reads[Metadata] = (
      (JsPath \ "id").readNullable[String].map(_.getOrElse(java.util.UUID.randomUUID.toString)) and
        (JsPath \ "name").read[String] and
        (JsPath \ "user_save_timestamp").read[String].map(x => fmt.parseDateTime(x).toDate) and
        (JsPath \ "auto_save_timestamp").read[String].map(x => fmt.parseDateTime(x).toDate) and
        (JsPath \ "language_info").readNullable[LanguageInfo].map(_.getOrElse(scala)) and
        (JsPath \ "trusted").readNullable[Boolean].map(_.getOrElse(true)) and
        (JsPath \ "sparkNotebook").readNullable[Map[String, String]] and
        (JsPath \ "customLocalRepo").readNullable[String] and
        (JsPath \ "customRepos").readNullable[List[String]] and
        (JsPath \ "customDeps").readNullable[List[String]] and
        (JsPath \ "customImports").readNullable[List[String]] and
        (JsPath \ "customArgs").readNullable[List[String]] and
        (JsPath \ "customSparkConf").readNullable[JsObject] and
        (JsPath \ "customVars").readNullable[Map[String,String]]
      )(Metadata.apply _)

    val w: Writes[Metadata] =
      OWrites { (m: Metadata) =>
        val name = JsString(m.name)
        val user_save_timestamp = JsString(fmt.print(new DateTime(m.user_save_timestamp)))
        val auto_save_timestamp = JsString(fmt.print(new DateTime(m.auto_save_timestamp)))
        val language_info = languageInfoFormat.writes(m.language_info)
        val trusted = JsBoolean(m.trusted)
        Json.obj(
          "id" → m.id,
          "name" → name,
          "user_save_timestamp" → user_save_timestamp,
          "auto_save_timestamp" → auto_save_timestamp,
          "language_info" → language_info,
          "trusted" → trusted,
          "sparkNotebook"→ m.sparkNotebook,
          "customLocalRepo" → m.customLocalRepo,
          "customRepos" → m.customRepos,
          "customDeps" → m.customDeps,
          "customImports" → m.customImports,
          "customArgs" → m.customArgs,
          "customSparkConf" → m.customSparkConf,
          "customVars" -> m.customVars
        )
      }

    Format(r, w)
  }

  implicit val cellReads: Reads[Cell] = Reads { (js: JsValue) =>
    val tpe = (js \ "cell_type").as[String]
    tpe match {
      case "code" | "output" => codeCellFormat.reads(js)
      case "heading"         => headingCellFormat.reads(js)
      case "markdown"        => markdownCellFormat.reads(js)
      case "raw"             => rawCellFormat.reads(js)
      case x                 =>
        throw new IllegalStateException("Cannot read this cell_type: " + x)
    }
  }
  implicit val cellWrites: Writes[Cell] = Writes { (c: Cell) =>
    c match {
      case c: CodeCell     => codeCellFormat.writes(c)
      case c: HeadingCell  => headingCellFormat.writes(c)
      case c: MarkdownCell => markdownCellFormat.writes(c)
      case c: RawCell      => rawCellFormat.writes(c)
    }
  }
  implicit val cellFormat: Format[Cell] = Format(cellReads, cellWrites)

  case class Worksheet(cells: List[Cell])

  implicit val worksheetFormat = Json.format[Worksheet]

  case class SerNotebook(metadata: Option[Metadata] = None,
                          cells: Option[List[Cell]] = Some(Nil),
                          worksheets: Option[List[Worksheet]] = None,
                          autosaved: Option[List[Worksheet]] = None,
                          nbformat: Option[Int]
                        )

  implicit val notebookFormat = Json.format[SerNotebook]

  def fromJson(content: String)(implicit ex : ExecutionContext): Future[SerNotebook] = {
    val fJson = Future {
      Json.parse(content)
    }.recoverWith{case ex:Throwable => Future.failed[JsValue](new NotebookDeserializationException("Cannot parse JSON", ex))}

    fJson.map(json => json.validate[SerNotebook]).flatMap{
      case s: JsSuccess[SerNotebook] => {
        s.get match {
          case SerNotebook(None,None,None,None,None) =>
            Future.failed(new EmptyNotebookException)
          case notebook =>
            Future.successful(notebook.cells.map { _ => notebook } getOrElse notebook.copy(cells = Some(Nil)) )
        }
      }
      case e: JsError => {
        Future.failed(new NotebookDeserializationException(Json.stringify(JsError.toFlatJson(e)), null))
      }
    }
  }

  def toJson(sn: SerNotebook)(implicit ex : ExecutionContext) : Future[String] = {
    Future {
      Json.prettyPrint(notebookFormat.writes(sn))
    }
  }



}