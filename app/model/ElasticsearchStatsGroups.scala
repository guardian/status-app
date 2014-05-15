package model

import play.api.libs.json.{JsObject, JsValue}
import lib.UptimeDisplay
import org.joda.time.Duration

object ElasticsearchStatsGroups {
  def parse(js: JsValue): List[Node] = {
    val JsObject(nodes) = js \ "nodes"

    nodes.toList.map { case (_, json) => Node(json) }
  }


  case class StatsGroup(name: String, queryTime: Long, queryCount: Long) {
    lazy val averageRequestTime = if (queryCount == 0) 0 else queryTime / queryCount
    lazy val humanTime = UptimeDisplay.print(new Duration(queryTime))
  }

  object StatsGroup {
    def apply(name: String, v: JsValue): StatsGroup =
      StatsGroup(name, (v \ "query_time_in_millis").as[Long], (v \ "query_total").as[Long])

    def withOverallTotal(s: List[StatsGroup]): List[StatsGroup] = {
      val total = StatsGroup("(overall)", s.map(_.queryTime).sum, s.map(_.queryCount).sum)
      total :: s.sortBy(- _.queryTime)
    }
  }

  case class Node(name: String, statsGroups: Seq[StatsGroup])
  object Node {
    def apply(nodeInfo: JsValue): Node = {
      val nodeName = (nodeInfo \ "name").as[String]
      val JsObject(groupsJson) = nodeInfo \ "indices" \ "search" \ "groups"

      val stats = for ((name, groups) <- groupsJson) yield StatsGroup(name, groups)

      Node(nodeName, StatsGroup.withOverallTotal(stats.toList))
    }
  }
}
