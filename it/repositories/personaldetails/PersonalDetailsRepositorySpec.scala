package repositories.personaldetails

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetailsExamples._
import org.joda.time.DateTime
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

class PersonalDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName: String = CollectionNames.APPLICATION

  def repository = new PersonalDetailsMongoRepository(ITDateTimeFactoryMock, mongo)
  def appRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  "update candidate" should {
    "modify the details and find the personal details successfully" in {
      val personalDetails = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      val applicationStatus = appRepository.findStatus(AppId).futureValue

      personalDetails mustBe JohnDoe
      applicationStatus.status mustBe IN_PROGRESS.toString
      timesApproximatelyEqual(applicationStatus.statusDate.get, DateTime.now()) mustBe true
    }

    "do not update the application in different status than required" in {
      val actualException = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).failed.futureValue

      actualException mustBe PersonalDetailsNotFound(AppId)
    }

    "modify the details and find the personal details successfully without changing application status" in {
      val personalDetails = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED))
        _ <- repository.updateWithoutStatusChange(AppId, UserId, JohnDoe)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      personalDetails mustBe JohnDoe
    }
  }

  "find candidate" should {
    "throw an exception when it does not exist" in {
      val result = repository.find(AppId).failed.futureValue
      result mustBe PersonalDetailsNotFound(AppId)
    }
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(ordered = false).one(doc)
}
