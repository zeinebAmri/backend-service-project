import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import spray.json._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.hibernate.{Session, Transaction}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.IOException
import java.sql.{DriverManager, ResultSet, SQLException}


case class ClientData(clientId: Int, cpuUsage: Int, device: String, os: String, clientVersion: String, errorMessage: String, stack: Array[String], timeStamp: LocalDateTime)

/**
 * Serialization and deserialization behavior for LocalDateTime instances in a JSON format
 * Provides a custom format for converting LocalDateTime instances to and from JSON
 * Required when receiving data in a JSON format
 */
object ClientDataJsonProtocol extends DefaultJsonProtocol {

  implicit val dateFormat = new JsonFormat[LocalDateTime] {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    override def write(obj: LocalDateTime): JsValue = JsString(formatter.format(obj))

    override def read(json: JsValue): LocalDateTime = json match {
      case JsString(s) => LocalDateTime.parse(s, formatter)
      case _ => throw DeserializationException("LocalDateTime expected")
    }
  }
  implicit val clientDataFormat = jsonFormat8(ClientData)
}

object BackendService {
  val logger = LoggerFactory.getLogger(getClass)
  var clientData: Future[ClientData] = _
  implicit val system = ActorSystem("data-service")

  implicit val executionContext = system.dispatcher


  val route = path("data") {
    post {
      // extract the request body as a string
      entity(as[String]) { jsonString =>
        import ClientDataJsonProtocol._
        //Get the session to interact with the database
        val session = HibernateUtil.getSessionFactory.openSession()
        val tx = session.beginTransaction()
        //use the spray-json library to parse the string into a ClientData case class object
        clientData = Future {
          jsonString.parseJson.convertTo[ClientData]
        }
        clientData.onComplete {
          case Success(data) =>
            try {
              storeData(data, session, tx)
              println("done storing")
              retrieveDataFromDB()
            } catch {
              case e: IOException =>
                logger.error("Error reading data from client: " + e.getMessage)
              case e =>
                logger.error("Unknown error occurred: " + e.getMessage)
            } finally {
              session.getSessionFactory.close()
            }
          case Failure(ex) => println(ex)
        }
        complete(HttpResponse(StatusCodes.OK))
      }
    }
  }

  /**
   *
   * @param data
   * @param session
   * @param tx
   * Method to store the data received from the client in the database
   */
  def storeData(data: ClientData, session: Session, tx: Transaction): Unit = {
    val cpu = new Cpu()
    val stackAsString = data.stack.mkString("|")
    cpu.clientId = data.clientId
    cpu.device = data.device
    cpu.cpuUsage = data.cpuUsage
    cpu.clientVersion = data.clientVersion
    cpu.errorMessage = data.errorMessage
    cpu.stackTrace = stackAsString
    cpu.os = data.os
    cpu.timeStamp = data.timeStamp
    session.persist(cpu)
    session.flush()
    tx.commit()
  }

  /**
   * Fetch data from the database
   */
  def retrieveDataFromDB(): Unit = {

    val url = "jdbc:postgresql://localhost:5432/postgres"
    // create a connection to the database
    val connection = DriverManager.getConnection(url)

    try {
      // create a statement
      val statement = connection.createStatement()
      //execute the query
      val resultSet: ResultSet = statement.executeQuery("SELECT * FROM cpu WHERE cpuusage >= 80")
      analyzeData(resultSet)
    } catch {
      case e: SQLException =>
        logger.error("Error with SQL query or database connection: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  /**
   * This method analyzes the data fetched from the database
   * and processes it (most frequent error message, average CPu usage, the most common faulty OS and Device versions etc...)
   */
  def analyzeData(resultSet: ResultSet): Unit = {
    var count = 0
    var totalCpuUsage = 0
    //Stores unique values only (no duplicates)
    var uniqueClientIds = Set[(String, String)]()
    var errorMessages = List[String]()
    var errorMessageCount = 0
    val errorCounts = scala.collection.mutable.HashMap[String, Int]()
    val deviceCounts = scala.collection.mutable.HashMap[String, Int]()
    val osCounts = scala.collection.mutable.HashMap[String, Int]()

    // iterate through the result set
    while (resultSet.next()) {
      val clientId = resultSet.getString("clientid")
      val clientVersion = resultSet.getString("clientversion")
      uniqueClientIds += ((clientId, clientVersion))
      val device = resultSet.getString("device")
      if (!deviceCounts.contains(device)) {
        deviceCounts += (device -> 1)
      } else {
        deviceCounts(device) += 1
      }
      val cpuUsage = resultSet.getInt("cpuusage")
      totalCpuUsage += cpuUsage
      count += 1
      val errorMessage = resultSet.getString("errormessage")
      errorMessages = errorMessage :: errorMessages
      errorMessageCount += 1
      if (!errorCounts.contains(errorMessage)) {
        errorCounts += (errorMessage -> 1)
      } else {
        errorCounts(errorMessage) += 1
      }
      val os = resultSet.getString("os")
      if (!osCounts.contains(os)) {
        osCounts += (os -> 1)
      } else {
        osCounts(os) += 1
      }
    }
    // some data processing
    val averageHighCpuUsage = totalCpuUsage / count
    val distinctErrorMessages = errorMessages.toSet
    val uniqueErrorMessageCount = distinctErrorMessages.size
    val mostFrequentErrorMessage = errorCounts.maxBy(_._2)._1
    val mostCommonFaultyOs = osCounts.maxBy(_._2)._1
    val mostCommonFaultyDevice = deviceCounts.maxBy(_._2)._1
    logResults(averageHighCpuUsage, count, uniqueClientIds, errorMessageCount, uniqueErrorMessageCount, distinctErrorMessages, mostFrequentErrorMessage, mostCommonFaultyOs, mostCommonFaultyDevice )
  }

  /**
   *
   * @param averageHighCpuUsage
   * @param count
   * @param uniqueClientIds
   * @param errorMessageCount
   * @param uniqueErrorMessageCount
   * @param distinctErrorMessages
   * @param frequentMessage
   * @param commonOs
   * @param commonDevice
   * Logging method for the different values we have processed in analyzeData
   */
  def logResults(averageHighCpuUsage: Int, count: Int, uniqueClientIds: Set[(String, String)], errorMessageCount: Int, uniqueErrorMessageCount: Int, distinctErrorMessages: Set[String], frequentMessage: String, commonOs: String, commonDevice: String) = {
    logger.warn(s"Average CPU usage: $averageHighCpuUsage, Number of affected clients: $count, Number of unique client IDs: ${uniqueClientIds.size}, the uniqueIds and their release version: $uniqueClientIds")
    logger.warn(s"Number of error messages: $errorMessageCount, Number of unique error messages: $uniqueErrorMessageCount, Distinct error messages: $distinctErrorMessages, The most frequent error message is: $frequentMessage")
    logger.warn(s"The most common faulty OS is: $commonOs and the most common faulty device is: $commonDevice")
  }

  def main(args: Array[String]): Unit = {
    Http().bindAndHandle(route, "localhost", 8080)
  }
}