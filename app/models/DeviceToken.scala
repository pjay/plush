package models

import java.util.Date
import com.redis._
import com.redis.serialization._
import Parse.Implicits.parseLong

case class DeviceToken(appKey: String, value: String, lastRegistrationDate: Date)

object DeviceToken extends RedisConnection {

  def findAllByAppKey(appKey: String) = {
    def iterate(result: Iterable[Option[String]], acc: List[DeviceToken]): List[DeviceToken] = result match {
      case Some(value) :: Some(time) :: rest => {
        iterate(rest, DeviceToken(appKey, value, new Date(time.toLong)) :: acc)
      }
      case _ => acc.reverse
    }

    val result = redis.sort[String]("app:" + appKey + ":device_tokens", None, true, false, Some("device_token:" + appKey + ":*"), List("#", "device_token:" + appKey + ":*"))
    iterate(result.flatten, List())
  }

  def findByAppKeyAndValue(appKey: String, value: String) =
    redis.get("device_token:" + appKey + ":" + value) map { time =>
      DeviceToken(appKey, value, new Date(time.toLong))
    } getOrElse None

  def countAllByAppKey(appKey: String): Long =
    redis.scard("app:" + appKey + ":device_tokens").getOrElse(0)

  def create(appKey: String, value: String): Option[DeviceToken] = {
    val time = new Date().getTime()
    redis.pipeline { p =>
      p.set("device_token:" + appKey + ":" + value, time)
      p.sadd("app:" + appKey + ":device_tokens", value)
    }
    // TODO: error handling
    Some(DeviceToken(appKey, value, new Date(time)))
  }

  def delete(appKey: String, value: String): Boolean = {
    redis.pipeline { p =>
      p.srem("app:" + appKey + ":device_tokens", value)
      p.del("device_token:" + appKey + ":" + value)
    }
    // TODO: error handling
    true
  }

}