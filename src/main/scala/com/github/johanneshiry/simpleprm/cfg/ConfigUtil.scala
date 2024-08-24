/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.cfg

import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg.MailProtocol

object ConfigUtil {

  def protocolFromConfig(protocol: SimplePrmCfg.MailProtocol): String = {
    protocol match {
      case MailProtocol.smtp => "smtp"
    }
  }

}
