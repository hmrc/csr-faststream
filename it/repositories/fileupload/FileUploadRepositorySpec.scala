package repositories.fileupload

import org.scalatest.Tag
import play.api.libs.iteratee.Iteratee
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FileUploadRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.FILE_UPLOAD
  lazy val repository: FileUploadMongoRepository = new FileUploadMongoRepository()

  "add" must {
    "store a file with contentType" taggedAs TravisIgnore in {
      val testContent = "Test contents".toCharArray.map(_.toByte)
      val testContentType = "application/pdf"
      val id = repository.add(testContentType, testContent).futureValue
      val retrievedFile = repository.retrieve(id).futureValue
      retrievedFile.contentType mustBe testContentType
      retrievedFile.fileContents.run(Iteratee.consume[Array[Byte]]()).futureValue mustBe testContent
    }
  }

  "retrieve" must {
    "retrieve a file by id" taggedAs TravisIgnore in {
      val testContent = "Test contents".toCharArray.map(_.toByte)
      val testContentType = "application/pdf"
      val id = repository.add(testContentType, testContent).futureValue
      val retrievedFile = repository.retrieve(id).futureValue
      retrievedFile.contentType mustBe testContentType
      retrievedFile.fileContents.run(Iteratee.consume[Array[Byte]]()).futureValue mustBe testContent
    }
  }
}

// This test fails on Travis but works locally and on jenkins by tagging it so we can
// explicitly skip it when travis runs
object TravisIgnore extends Tag("TravisIgnore")