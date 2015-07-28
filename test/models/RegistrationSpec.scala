package models

import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Play


class RegistrationSpec extends PlaySpec with OneAppPerSuite {
  val testAppKey = "testAppKey"
  val testValue = "testValue"

  "Registration" must {

    "create a registration" in {
      val created = Registration.create(testAppKey, testValue)
      assert(created.nonEmpty)
      assert(created.get.appKey == testAppKey)
      assert(created.get.value == testValue)
    }

    "count all by app key" in {
      val count = Registration.countAllByAppKey(testAppKey)
      assert(count == 1L)
    }

    "find all by app key" in {
      val found = Registration.findAllByAppKey(testAppKey)
      assert(found.length == 1)
      assert(found.head.appKey == testAppKey)
      assert(found.head.value == testValue)
    }

    "find by app key and value" in {
      val found = Registration.findByAppKeyAndValue(testAppKey, testValue)
      assert(found.nonEmpty)
      assert(found.get.appKey == testAppKey)
      assert(found.get.value == testValue)
    }

    "delete a registration" in {
      val deleted = Registration.delete(testAppKey, testValue)
      assert(deleted)
    }
  }
}
