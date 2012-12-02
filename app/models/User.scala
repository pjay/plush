package models

case class User(email: String, encryptedPassword: String, passwordSalt: String)

object User extends RedisModel {

  def fromMap(attrs: Map[String, String]): User = {
    User(attrs.getOrElse("email", ""),
         attrs.getOrElse("encryptedPassword", ""),
         attrs.getOrElse("passwordSalt", ""))
  }

  def authenticate(email: String, password: String) = User.findByEmail(email) map { user =>
    PasswordUtil.authenticate(password, PasswordUtil.toByteArray(user.encryptedPassword), PasswordUtil.toByteArray(user.passwordSalt))
  } getOrElse false

  def findByEmail(email: String) = redis.hgetall("user:" + email) map { m =>
    if (m.nonEmpty) Some(fromMap(m)) else None
  } getOrElse None

  def create(email: String, password: String) = {
    val passwordSalt = PasswordUtil.generateSalt
    val encryptedPassword = PasswordUtil.encryptPassword(password, passwordSalt)
    val attrs = Map("email" -> email, "encryptedPassword" -> PasswordUtil.toString(encryptedPassword), "passwordSalt" -> PasswordUtil.toString(passwordSalt))
    createOrUpdateHash("user:" + email, attrs)
  }

}

// http://www.javacodegeeks.com/2012/05/secure-password-storage-donts-dos-and.html
object PasswordUtil {

  import java.nio.charset.Charset
  import java.security.{NoSuchAlgorithmException, SecureRandom}
  import java.security.spec.{InvalidKeySpecException, KeySpec}
  import java.util.Arrays

  import javax.crypto.SecretKeyFactory
  import javax.crypto.spec.PBEKeySpec

  def authenticate(attemptedPassword: String, encryptedPassword: Array[Byte], salt: Array[Byte]) = {
    val encryptedAttemptedPassword = encryptPassword(attemptedPassword, salt)
    Arrays.equals(encryptedPassword, encryptedAttemptedPassword)
  }

  def encryptPassword(password: String, salt: Array[Byte]) = {
    val spec = new PBEKeySpec(password.toCharArray, salt, 10000, 160)
    val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    f.generateSecret(spec).getEncoded
  }

  def generateSalt = {
    val random = SecureRandom.getInstance("SHA1PRNG")
    val salt: Array[Byte] = Array(0, 0, 0, 0, 0, 0, 0, 0)
    random.nextBytes(salt)
    salt
  }

  def toString(bytes: Array[Byte]) =
    new sun.misc.BASE64Encoder().encode(bytes)

  def toByteArray(s: String) =
    new sun.misc.BASE64Decoder().decodeBuffer(s)

}