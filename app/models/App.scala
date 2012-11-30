package models

import com.redis._

case class App(userId: Long, name: String, key: String, secret: String, masterSecret: String,
  appMode: Int, debugMode: Boolean, iosCertPassword: Option[String], gcmApiKey: Option[String]) extends RedisModel {

  def update(attrs: Map[String, Any]) =
    createOrUpdateHash("app:" + key, attrs)

  def delete = {
    val deviceTokens = redis.smembers("app:" + key + ":device_tokens").flatten.flatten.map("device_token:" + key + ":" + _)
    val registrations = redis.smembers("app:" + key + ":registrations").flatten.flatten.map("registration:" + key + ":" + _)

    redis.pipeline { p =>
      p.del("app:" + key)
      p.srem("apps", key)
      if (deviceTokens.nonEmpty) p.del(deviceTokens.head, deviceTokens.tail.toSeq: _*)
      p.del("app:" + key + ":device_tokens")
      if (registrations.nonEmpty) p.del(registrations.head, registrations.tail.toSeq: _*)
      p.del("app:" + key + ":registrations")
    } match {
      case Some(Some(1) :: Some(1) :: _) => true
      case _ => false
    }
  }

  def certFile = {
    import java.io.File
    import play.api.Play
    import play.api.Play.current
    Play.application.getFile("certs/" + key + ".pem")
  }

  def isIosEnabled = {
    import java.io.File
    val cert = certFile
    cert.exists && cert.isFile && cert.canRead
    // TODO: check cert password, try to read the cert with the password
  }

  def isGcmEnabled =
    !gcmApiKey.isEmpty

}

object App extends RedisModel {

  def fromMap(attrs: Map[String, String]): App = {
    App(attrs.getOrElse("userId", "0").toLong,
        attrs.getOrElse("name", ""),
        attrs.getOrElse("key", ""),
        attrs.getOrElse("secret", ""),
        attrs.getOrElse("masterSecret", ""),
        attrs.getOrElse("appMode", "0").toInt,
        attrs.getOrElse("debugMode", "false").toBoolean,
        attrs.get("iosCertPassword"),
        attrs.get("gcmApiKey"))
  }

  def toMap(app: App): Map[String, Any] =
    (Map[String, Any]() /: app.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(app).toString)
    }

  def all: List[App] =
    redis.sort("apps", None, false, true, Some("app:*->name"), Nil).flatten.flatten.map(key => redis.hgetall("app:" + key)).flatten.map(fromMap(_)).toList

  def findByKey(key: String): Option[App] = redis.hgetall("app:" + key) match {
    case Some(map: Map[_, _]) if map.nonEmpty => Some(fromMap(map))
    case _ => None
  }

  def create(userId: Long, name: String, appMode: Int, debugMode: Boolean, iosCertPassword: Option[String], gcmApiKey: Option[String]): Option[String] = {
    val key = RandomGenerator.generateKey(22)
    val secret = RandomGenerator.generateSecret(22)
    val masterSecret = RandomGenerator.generateSecret(22)

    redis.sismember("apps", key) match {
      case false => {
        val attrs = Map("userId" -> userId, "name" -> name, "key" -> key, "secret" -> secret,
          "masterSecret" -> masterSecret, "appMode" -> appMode, "debugMode" -> debugMode,
          "iosCertPassword" -> iosCertPassword, "gcmApiKey" -> gcmApiKey)
        createOrUpdateHash("app:" + key, attrs) match {
          case true => redis.sadd("apps", key) map { l => Some(key) } getOrElse None
          case false => None
        }
      }
      case true => create(userId, name, appMode, debugMode, iosCertPassword, gcmApiKey)
    }
  }

}

object RandomGenerator {

  import scala.util.Random

  lazy val random = new Random()
  lazy val charsForKey = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  lazy val charsForSecret = charsForKey ++ List('-', '_')

  def generateKey(length: Int) = generateRandomString(length, charsForKey)

  def generateSecret(length: Int) = generateRandomString(length, charsForSecret)

  def generateRandomString(length: Int, chars: Seq[Char]) =
    (1 to length map { _ => chars(random.nextInt(chars.length)) }).mkString

}