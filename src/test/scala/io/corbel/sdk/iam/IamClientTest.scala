package io.corbel.sdk.iam

import io.corbel.sdk.AuthenticationProvider
import io.corbel.sdk.config.CorbelConfig
import io.corbel.sdk.error.ApiError
import org.json4s.JsonAST.JObject
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest._
import org.mockserver.model.HttpResponse._
import org.mockserver.model.JsonSchemaBody._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * @author Alexander De Leon (alex.deleon@devialab.com)
  */
class IamClientTest extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures with PatienceConfiguration {

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  var mockServer: ClientAndServer = null
  implicit val config = CorbelConfig(
    iamBaseUri = "http://localhost:1080",
    resourceBaseUri = "http://localhost:1080"
  )
  val clientId = "123"
  val cleintSecret = "567"
  val testToken = "AAAABBBCCCC"

  behavior of "authenticate"

  it should "make a correct token request" in {
    mockServer
      .when(authenticationRequest)
      .respond(
        response()
          .withStatusCode(201)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "accessToken": "valid-token",
              |  "expiresAt": 1385377605000,
              |  "refreshToken": "refresh-token"
              |}
            """.stripMargin)
      )

    val iam = IamClient()
    val clientCredentials = ClientCredentials(clientId, cleintSecret)
    val futureResponse = iam.authenticate(clientCredentials)

    whenReady(futureResponse) { response =>
      response should be(Right(AuthenticationResponse("valid-token", 1385377605000l, Some("refresh-token"))))
    }
  }

  it should "catch API errors" in {
    mockServer
      .when(authenticationRequest)
      .respond(
        response()
          .withStatusCode(401)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "error": "Unauthorized",
              |  "errorDescription": "Invalid credentials",
              |}
            """.stripMargin)
      )


    val iam = IamClient()
    val clientCredentials = ClientCredentials(clientId, cleintSecret)
    val futureResponse = iam.authenticate(clientCredentials)

    whenReady(futureResponse) { response =>
      response should be(Left(ApiError(401, Some("Unauthorized"), Some("Invalid credentials"))))
    }
  }

  behavior of "getUser"

  it should "make request to GET user by Id" in {
    val userId = "123"
    mockServer
      .when(getUserRequest(userId))
      .respond(
        response()
          .withStatusCode(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "id": "fdad3eaa19ddec84b397a9e9a2312262",
              |  "domain": "silkroad",
              |  "email": "alexander.deleon@bqreaders.com",
              |  "username": "alexander.deleon@bqreaders.com",
              |  "firstName": "alex",
              |  "scopes": [],
              |  "properties": {}
              |}
            """.stripMargin)
      )

    implicit val auth = AuthenticationProvider(testToken)
    val iam = IamClient()
    val futureResponse = iam.getUser(userId)

    whenReady(futureResponse) { response =>
      response should be(Right(User(
        id = Some("fdad3eaa19ddec84b397a9e9a2312262"),
        domain = Some("silkroad"),
        email = Some("alexander.deleon@bqreaders.com"),
        firstName = Some("alex"),
        username = Some("alexander.deleon@bqreaders.com"),
        scopes = Some(Seq.empty),
        properties = Some(JObject())
      )))
    }
  }


  /* ---------------- helper methods -- */
  def authenticationRequest = request()
    .withMethod("POST")
    .withPath("/v1.0/oauth/token")
    .withBody(jsonSchema(
      """
        |{
        | "type":"object", "properties":{
        |   "assertion": {"type": "string"},
        |   "grant_type": {"type": "string"}
        | }
        |}
      """.stripMargin))

  def getUserRequest(userId: String) = request()
      .withMethod("GET")
      .withPath(s"/v1.0/user/$userId")
      .withHeader("Authorization", s"Bearer $testToken")

  before {
    mockServer = startClientAndServer(1080)
  }

  after {
    mockServer.stop()
  }


}
