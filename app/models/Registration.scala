package models

import java.util.Date
import com.redis._
import com.redis.serialization._
import Parse.Implicits.parseLong

case class Registration(appKey: String, value: String, lastRegistrationDate: Date) {

  def delete = Registration.delete(appKey, value)

}

object Registration extends RedisConnection {

  def findAllByAppKey(appKey: String, limit: Option[(Int, Int)] = None) = {
    def iterate(result: Iterable[Option[String]], acc: List[Registration]): List[Registration] = result match {
      case Some(value) :: Some(time) :: rest => {
        iterate(rest, Registration(appKey, value, new Date(time.toLong)) :: acc)
      }
      case _ => acc.reverse
    }

    val by = Some("registration:" + appKey + ":*")
    val get = List("#", "registration:" + appKey + ":*")
    val result = redis.sort[String]("app:" + appKey + ":registrations", limit, true, false, by, get)
    result map (iterate(_, List())) getOrElse List()
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