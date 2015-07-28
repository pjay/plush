package models

import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}

class AppSpec extends PlaySpec with OneAppPerSuite {
  val testUserId = 123L
  val testAppName = "testAppName"
  val testAppMode = 0
  val testAppDebugMode = true
  val testAppIosCertPass = Some("testIosCertPass")
  val testAppGcmKey = Some("testGcmKey")

  var testApp: Option[App] = None
  var testAppKey: Option[String] = None

  "App" must {
    "create an app" in {
      val created = App.create(testUserId, testAppName, testAppMode, testAppDebugMode, testAppIosCertPass, testAppGcmKey)
      assert(created.nonEmpty)
      testAppKey = created
    }

    "find an app by key" in {
      val found = App.findByKey(testAppKey.get)
      assert(found.nonEmpty)
      assert(found.get.name == testAppName)
      assert(found.get.userId == testUserId)
      assert(found.get.iosCertPassword == testAppIosCertPass)
      assert(found.get.gcmApiKey == testAppGcmKey)
    }

    "find all" in {
      val found = App.all
      assert(found.nonEmpty)
      assert(found.head.name == testAppName)
      assert(found.head.userId == testUserId)
      assert(found.head.iosCertPassword == testAppIosCertPass)
      assert(found.head.gcmApiKey == testAppGcmKey)
    }

  }
}
