package repositories.stc

import model.persisted.StcEvent
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class StcEventMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.EVENT

  lazy val repository = new StcEventMongoRepository()

  "Stop the Clock Event repository" should {
    "insert new event" in {
      val event = StcEvent("ExampleEvent", DateTime.now(DateTimeZone.UTC), Some("appId"), Some("userId"))
      repository.create(event).futureValue
      val result = getEvent(repository.collection.find(BSONDocument.empty).one[BSONDocument])

      result mustBe event
    }
  }

  private def getEvent(doc: Future[Option[BSONDocument]]): StcEvent =
    doc.map(_.map(StcEvent.eventHandler.read)).futureValue.get
}
