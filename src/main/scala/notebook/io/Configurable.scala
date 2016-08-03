package notebook.io

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}

trait Configurable[T] {
  def apply(config: Config = ConfigFactory.empty())(implicit ec:ExecutionContext) : Future[T]
}

class ConfigurationMissingException(val key: String) extends Exception(s"Key missing: [$key]")
class ConfigurationCorruptException(msg: String) extends Exception(msg)
