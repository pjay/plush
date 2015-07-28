package models

import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Play


class EventSpec extends PlaySpec with OneAppPerSuite {
  val testAppKey = "testAppKey"
  val testSeverity = 0.toShort
  val testMessage = "testMessage"

  "Event" must {
    "start the FakeApplication" in {
      Play.maybeApplication mustBe Some(app)
    }

    "create an event" in {
      val created = Event.create(testAppKey, testSeverity, testMessage)
      assert(created)
    }


    "count all by app key" in {
      val count = Event.countAllByAppKey(testAppKey)
      assert(count == 1)
    }

    "find all by app key" in {
      val found = Event.findAllByAppKey(testAppKey)
      assert(found.length == 1)
      assert(found.head.message == testMessage)
      assert(found.head.severity == testSeverity)
    }

    "remove by app key" in {
      val removed = Event.deleteByAppKey(testAppKey)
      assert(removed.nonEmpty)
      assert(removed.get == 1L)
    }

  }
}
