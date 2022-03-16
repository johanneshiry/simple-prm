/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import com.github.sardine.impl.SardineImpl
import com.github.sardine.{DavResource, Sardine, SardineFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.http.conn.ssl.{
  NoopHostnameVerifier,
  SSLConnectionSocketFactory,
  TrustSelfSignedStrategy
}
import org.apache.http.ssl.SSLContexts

import java.net.URI
import java.util
import scala.jdk.CollectionConverters.*
import scala.util.Try

// wraps a sardine card dav client for single uri operation mode
case class SardineClientWrapper(serverUri: URI, sardine: Sardine) {

  /** Tries to get a directory listing using WebDAV <code>PROPFIND</code>.
    *
    * @return
    *   List of resources for the serverUri on success, IOException otherwise
    */
  def listDir: Try[Seq[DavResourceWrapper]] = Try(
    sardine
      .list(serverUri.toString)
      .asScala
      .filterNot(_.toString.equals(serverUri.getPath))
      .map(DavResourceWrapper(_, serverUri))
      .toSeq
  )

}

case object SardineClientWrapper extends LazyLogging {

  def apply(
      serverUri: URI,
      username: String,
      password: String
  ): SardineClientWrapper = createClient(serverUri, username, password)

  private def createClient(
      serverUri: URI,
      username: String,
      password: String
  ): SardineClientWrapper = {

    // todo initial directory list to ensure that the server can be reached with provided url and credentials
    val sardine = createSecureClient(username, password)
    SardineClientWrapper(serverUri, sardine)
  }

  private def createSecureClient(username: String, password: String) =
    SardineFactory.begin(username, password)

  private def createUnsecureClient(
      serverUri: URI,
      username: String,
      password: String
  ): Sardine = {
    logger.warn(
      s"Using unsecure connection without SSL certificate check to connect to $serverUri!"
    )

    val sslcontext =
      SSLContexts.custom.loadTrustMaterial(new TrustSelfSignedStrategy).build
    val socketFactory =
      new SSLConnectionSocketFactory(sslcontext, NoopHostnameVerifier.INSTANCE)

    new SardineImpl(username, password) {
      override protected def createDefaultSecureSocketFactory
          : SSLConnectionSocketFactory = socketFactory
    }

  }

}
