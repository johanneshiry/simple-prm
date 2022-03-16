/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import com.github.sardine.DavResource

import java.net.URI

final case class DavResourceWrapper(davResource: DavResource, serverUri: URI) {

  def fullPath: String =
    s"${serverUri.toURL.getProtocol}://${serverUri.getHost + davResource.getHref.getPath}"

}
