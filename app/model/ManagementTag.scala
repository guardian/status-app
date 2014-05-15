package model


case class ManagementTag(protocol: Option[String], port: Option[Int],
                         path: Option[String], format: Option[String])
object ManagementTag {
  def apply(tag: Option[String]): Option[ManagementTag] = {
    val KeyValue = """([^=]*)=(.*)""".r
    tag match {
      case Some("none") => None
      case Some(tagContent) =>
        Some({
          val params = tagContent.split(",").filterNot(_.isEmpty).flatMap {
            case KeyValue(key, value) => Some(key -> value)
            case _ => None
          }.toMap
          ManagementTag(params.get("protocol"), params.get("port").map(_.toInt), params.get("path"), params.get("format"))
        })
      case None => Some(ManagementTag(None, None, None, None))
    }
  }
}
