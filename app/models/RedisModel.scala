package models

import com.redis._

trait RedisConnection {

  val redis = new RedisClient("localhost", 6379)

}

trait RedisModel extends RedisConnection {

  def createOrUpdateHash(key: String, hash: Map[String, Any]) = {
    val noneFields = noneFieldsFromMap(hash)
    val valuesHash = valuesMapFromMap(hash)

    redis.pipeline { p =>
      if (noneFields.nonEmpty) p.hdel(key, noneFields.head, noneFields.tail.toSeq:_*)
      p.hmset(key, valuesHash)
    } match {
      // TODO: there should be a better way!
      case Some(List(Some(_), true)) => true
      case Some(List(true)) => true
      case _ => false
    }
  }

  def noneFieldsFromMap(m: Map[String, Any]) =
    m collect { case (k, None) => k }

  def valuesMapFromMap(m: Map[String, Any]) =
    (m -- noneFieldsFromMap(m)) map { case (k, Some(v)) => k -> v; case (k, v) => k -> v }

}