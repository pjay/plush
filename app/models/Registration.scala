package models

import java.util.Date
import com.redis._
import com.redis.serialization._
import Parse.Implicits.parseLong

case class Registration(appKey: String, value: String, lastRegistrationDate: Date)

object Registration extends RedisConnection {

  def findAllByAppKey(appKey: String) = {
    def iterate(result: Iterable[Option[String]], acc: List[Registration]): List[Registration] = result match {
      case Some(value) :: Some(time) :: rest => {
        iterate(rest, Registration(appKey, value, new Date(time.toLong)) :: acc)
      }
      case _ => acc.reverse
    }

    val result = redis.sort[String]("app:" + appKey + ":registrations", None, true, false, Some("registration:" + appKey + ":*"), List("#", "registration:" + appKey + ":*"))
    iterate(result.flatten, List())
  }

  def countAllByAppKey(appKey: String): Long =
    redis.scard("app:" + appKey + ":registrations").getOrElse(0)

  def create(appKey: String, value: String): Option[Registration] = {
    val time = new Date().getTime()
    redis.pipeline { p =>
      p.set("registration:" + appKey + ":" + value, time)
      p.sadd("app:" + appKey + ":registrations", value)
    }
    // TODO: error handling
    Some(Registration(appKey, value, new Date(time)))
  }

  def delete(appKey: String, value: String): Boolean = {
    redis.pipeline { p =>
      p.srem("app:" + appKey + ":registrations", value)
      p.del("registration:" + appKey + ":" + value)
    }
    // TODO: error handling
    true
  }

}