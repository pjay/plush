package models

import java.util.Date
import com.redis._
import com.redis.serialization._
import Parse.Implicits.parseLong

case class DeviceToken(appKey: String, value: String, lastRegistrationDate: Date) {

  def delete = DeviceToken.delete(appKey, value)

}

object DeviceToken extends RedisConnection {

  def findAllByAppKey(appKey: String, limit: Option[(Int, Int)] = None) = {
    def iterate(result: Iterable[Option[String]], acc: List[DeviceToken]): List[DeviceToken] = result match {
      case Some(value) :: Some(time) :: rest => {
        iterate(rest, DeviceToken(appKey, value, new Date(time.toLong)) :: acc)
      }
      case _ => acc.reverse
    }

    val by = Some("device_token:" + appKey + ":*")
    val get = List("#", "device_token:" + appKey + ":*")
    val result = redis.sort[String]("app:" + appKey + ":device_tokens", limit, true, false, by, get)
    result map (iterate(_, List())) getOrElse List()
  }

  def findByAppKeyAndValue(appKey: String, value: String) =
    redis.get("device_token:" + appKey + ":" + value.toUpperCase) map { time =>
      Some(DeviceToken(appKey, value, new Date(time.toLong)))
    } getOrElse None

  def countAllByAppKey(appKey: String): Long =
    redis.scard("app:" + appKey + ":device_tokens").getOrElse(0)

  def create(appKey: String, value: String): Option[DeviceToken] = {
    val time = new Date().getTime()
    val uppercaseValue = value.toUpperCase
    redis.pipeline { p =>
      p.set("device_token:" + appKey + ":" + uppercaseValue, time)
      p.sadd("app:" + appKey + ":device_tokens", uppercaseValue)
    }
    // TODO: error handling
    Some(DeviceToken(appKey, uppercaseValue, new Date(time)))
  }

  def delete(appKey: String, value: String): Boolean = {
    val uppercaseValue = value.toUpperCase
    redis.pipeline { p =>
      p.srem("app:" + appKey + ":device_tokens", uppercaseValue)
      p.del("device_token:" + appKey + ":" + uppercaseValue)
    }
    // TODO: error handling
    true
  }

}