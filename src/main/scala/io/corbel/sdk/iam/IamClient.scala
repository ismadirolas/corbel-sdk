package io.corbel.sdk.iam

import com.ning.http.client.Response
import dispatch._
import io.corbel.sdk.AuthenticationProvider
import io.corbel.sdk.config.CorbelConfig
import io.corbel.sdk.error.ApiError
import io.corbel.sdk.error.ApiError._
import io.corbel.sdk.http.CorbelHttpClient
import io.corbel.sdk.iam.IamClient._
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import pdi.jwt.{Jwt, JwtAlgorithm}
import CorbelHttpClient._

import _root_.scala.concurrent.{ExecutionContext, Future}

/**
  * Iam interface implementation
  *
  * @author Alexander De Leon (alex.deleon@devialab.com)
  */
class IamClient(implicit config: CorbelConfig) extends CorbelHttpClient with Iam {

  implicit val formats = DefaultFormats

  override def authenticationRefresh(clientCredentials: ClientCredentials, refreshToken: String, authenticationOptions: AuthenticationOptions)
                                    (implicit ec: ExecutionContext): Future[Either[ApiError,AuthenticationResponse]] = {
    val claims = Claims()
      .addClientCredentials(clientCredentials)
      .addRefreshToken(refreshToken)
      .addOptions(authenticationOptions)

    doAuthenticate(buildAssertion(claims, clientCredentials.secret))
  }

  override def authenticate(clientCredentials: ClientCredentials, userCredentials: Option[UserCredentials], authenticationOptions: AuthenticationOptions)
                           (implicit ec: ExecutionContext): Future[Either[ApiError,AuthenticationResponse]] = {
    val claims = Claims()
      .addClientCredentials(clientCredentials)
    for (userCredentials <- userCredentials) {
      claims.addUserCredentials(userCredentials)
    }
    claims.addOptions(authenticationOptions)

    doAuthenticate(buildAssertion(claims, clientCredentials.secret))
  }

  override def getUser(implicit authenticationProvider: AuthenticationProvider, ec: ExecutionContext): Future[Either[ApiError,User]] = {
    val req = jsonApi(iam / `user/me`).withAuth
    http(req > as[User].eitherApiError)
  }

  override def getUser(id: String)(implicit authenticationProvider: AuthenticationProvider, ec: ExecutionContext): Future[Either[ApiError,User]] = {
    val req = jsonApi(iam / `user/{id}`(id)).withAuth
    http(req > as[User].eitherApiError)
  }

  override def createUserGroup(userGroup: UserGroup)(implicit authenticationProvider: AuthenticationProvider, ec: ExecutionContext): Future[Either[ApiError, String]] = {
    val req = jsonApi(iam / group).withAuth << write(userGroup)
    http(req > response.eitherApiError).map(resp => resp.right.map {
      _.getHeader(HttpHeaders.Names.LOCATION) match {
        case GroupId(id) => id
        case loc => throw new IllegalStateException(s"Expecting correct group URI in Location header. I got $loc")
      }
    })
  }

  private def doAuthenticate(assertionParam: String)(implicit ec: ExecutionContext): Future[Either[ApiError,AuthenticationResponse]] = {
    val req = jsonApi(iam / `oauth/token`) <<
      compact(render((assertion -> assertionParam) ~ (grant_type -> `jwt-bearer`)))
    http(req > as[AuthenticationResponse].eitherApiError)
  }

  private def buildAssertion(claims: Claims, secret: String) = Jwt.encode(claims.toJson, key = secret, algorithm = JwtAlgorithm.HmacSHA256)

  private def as[T](implicit ct: Manifest[T]) = (response: Response) => read[T](response.getResponseBodyAsStream)
  private def response = (response: Response) => response
}


object IamClient {

  def apply()(implicit config: CorbelConfig) = new IamClient()

  //endpoints
  private val `oauth/token` = "v1.0/oauth/token"
  private def `user/{id}`(id: String) = s"v1.0/user/$id"
  private val `user/me` = `user/{id}`("me")
  private val group = "v1.0/group"

  private val assertion = "assertion"
  private val grant_type = "grant_type"
  private val `jwt-bearer` = "urn:ietf:params:oauth:grant-type:jwt-bearer"

  private val GroupId = """.*/group/(\w*)$""".r
}

