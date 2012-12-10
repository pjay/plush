package models

import com.redis._
import java.util.Date
import play.api.libs.json._

case class Event(date: Date, severity: Short, message: String) {

  def severityString = severity match {
    case 1 => "debug"
    case 2 => "info"
    case 3 => "warning"
    case 4 => "error"
    case 5 => "critical"
    case _ => "unknown"
  }

}

object Event {

  val redis = new RedisClient("localhost", 6379)
  val maxEventsCount = 1000

  object Severity {
    val UNKNOWN: Short = 0
    val DEBUG: Short = 1
    val INFO: Short = 2
    val WARNING: Short = 3
    val ERROR: Short = 4
    val CRITICAL: Short = 5
  }

  def findAllByAppKey(appKey: String, offset: Int, count: Int) = {
    redis.lrange("events:" + appKey, offset, count) map { list =>
      list.flatten map { jsonString =>
        val json = Json.parse(jsonString)
        val date = (json \ "date").asOpt[Long].getOrElse[Long](0)
        val severity = (json \ "severity").asOpt[Short].getOrElse[Short](0)
        val message = (json \ "message").asOpt[String].getOrElse("")
        Event(new Date(date), severity, message)
      }
    } getOrElse List()
  }

  def countAllByAppKey(appKey: String) =
    redis.llen("events:" + appKey).getOrElse[Long](0)

  def create(appKey: String, severity: Short, message: String) = {
    val log = Json.toJson(Map(
      "date" -> Json.toJson(System.currentTimeMillis()),
      "severity" -> Json.toJson(severity),
      "message" -> Json.toJson(message)
    ))
    redis.lpush("events:" + appKey, Json.stringify(log))
    redis.ltrim("events:" + appKey, 0, maxEventsCount - 1)
  }

}