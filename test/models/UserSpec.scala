package models

import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}

class UserSpec extends PlaySpec with OneAppPerSuite {
  val testEmail = "testEmail"
  val testPasswordRaw = "testPassword"

  "User" must {
    "create a user" in {
      val created = User.create(testEmail, testPasswordRaw)
      assert(created)
    }

    "authenticate a user" in {
      val authenticated = User.authenticate(testEmail, testPasswordRaw)
      assert(authenticated)
    }

    "find by email" in {
      val found = User.findByEmail(testEmail)
      assert(found.nonEmpty)
      assert(found.get.email == testEmail)
      assert(found.get.encryptedPassword.nonEmpty)
    }

    "delete a user" in {
      val deleted = User.delete(testEmail)
      assert(deleted.nonEmpty)
      assert(deleted.get == 1L)
    }

  }
}
