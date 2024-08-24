/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import com.github.sardine.DavResource

import java.net.URI

final case class DavResourceWrapper(davResource: DavResource, serverUri: URI) {

  def fullPath: String =
    s"$protocol$host$maybePort$resource"

  private def protocol = s"${serverUri.toURL.getProtocol}://"

  private def host = serverUri.getHost

  private def resource = davResource.getHref.getPath

  private def maybePort =
    if (serverUri.getPort != -1) s":${serverUri.getPort}" else ""

}
