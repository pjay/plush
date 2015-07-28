package models

import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Play


class DeviceTokenSpec extends PlaySpec with OneAppPerSuite {
  val testAppKey = "testAppKey"
  val testValue = "testValue"

  "DeviceToken" must {

    "create a device token" in {
      val created = DeviceToken.create(testAppKey, testValue)
      assert(created.nonEmpty)
      assert(created.get.appKey == testAppKey)
      assert(created.get.value == testValue.toUpperCase)
    }

    "count all by app key" in {
      val count = DeviceToken.countAllByAppKey(testAppKey)
      assert(count == 1L)
    }

    "find all by app key" in {
      val found = DeviceToken.findAllByAppKey(testAppKey)
      assert(found.length == 1)
      assert(found.head.appKey == testAppKey)
      assert(found.head.value == testValue.toUpperCase)
    }

    "find by app key and value" in {
      val found = DeviceToken.findByAppKeyAndValue(testAppKey, testValue)
      assert(found.nonEmpty)
      assert(found.get.appKey == testAppKey)
      assert(found.get.value == testValue)
    }

    "delete a device token" in {
      val deleted = DeviceToken.delete(testAppKey, testValue)
      assert(deleted)
    }
  }
}
