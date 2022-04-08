/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.notifier

import cats.effect.IO
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import emil.{Header, Mail}
import emil.builder.{
  CustomHeader,
  From,
  HtmlBody,
  MailBuilder,
  Subject,
  TextBody,
  To
}

final case class EmailComposer(from: String, to: String) {

  def compose(contactName: String, contactMail: String): Mail[IO] =
    MailBuilder.build(
      From(from),
      To(to),
      Subject(s"Stay in touch with $contactName"),
      CustomHeader(Header("User-Agent", "SimplePRM")),
      TextBody(textBody(contactName, contactMail))
    )

  def textBody(contactName: String, contactMail: String): String =
    s"Hi.\n\nPlease remember to stay in touch with $contactName ($contactMail)."

}

object EmailComposer {

  def apply(composerCfg: SimplePrmCfg.SimplePrm.Notifier.Email): EmailComposer =
    new EmailComposer(composerCfg.sender, composerCfg.receiver)

}
