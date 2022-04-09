/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.notifier

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.johanneshiry.simpleprm.cfg.{ConfigUtil, SimplePrmCfg}
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg.SSLType
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.model.{Contact, StayInTouch}
import com.github.johanneshiry.simpleprm.notifier.EmailNotifierService.{
  EmailNotifierServiceCmd,
  EmailsSend,
  StayInTouchData
}
import com.typesafe.scalalogging.LazyLogging
import emil.{Header, Mail, MailConfig, MimeType, SSLType}
import emil.javamail.JavaMailEmil

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{
  Duration,
  FiniteDuration,
  HOURS,
  MINUTES,
  SECONDS
}
import scala.util.{Failure, Success}
import scala.jdk.CollectionConverters.*

private[notifier] trait EmailNotifierService extends LazyLogging {

  private val emptyStringAsOption = (s: String) =>
    if s.isEmpty || s.isBlank then None else Some(s)

  def initDelay(runHour: Long): FiniteDuration = {
    val now = ZonedDateTime.now()
    val nowHour = now.getHour
    val nowMinute = now.getMinute

    val delayHour =
      if nowHour < runHour then runHour - nowHour else 24 - nowHour + runHour
    val delayMinutes = delayHour * 60 - nowMinute

    FiniteDuration(
      delayMinutes,
      MINUTES
    )
  }

  def sendMails(mailConfig: MailConfig, emails: Seq[emil.Mail[IO]])(implicit
      ec: ExecutionContext
  ): Future[Seq[NonEmptyList[String]]] =
    Future.sequence(
      emails.map(email => sendMail(mailConfig, email))
    )

  def sendMail(
      mailCfg: emil.MailConfig,
      email: emil.Mail[IO]
  )(implicit ec: ExecutionContext): Future[NonEmptyList[String]] = {
    import cats.effect._
    import emil._, emil.builder._
    import cats.effect.unsafe.implicits.global

    JavaMailEmil[IO]()
      .apply(mailCfg)
      .send(email)
      .onError(error => {
        logger.warn("Error while sending e-mails!", error)
        IO.pure({})
      })
      .unsafeToFuture()

  }

  def composeEmails(
      emailComposer: EmailComposer,
      stayInTouchData: Vector[StayInTouchData]
  ): Seq[Mail[IO]] =
    stayInTouchData.map(stayInTouchData =>
      emailComposer.compose(
        stayInTouchData.contact.vCard.getFormattedName.getValue,
        stayInTouchData.contact.vCard.getEmails.asScala.headOption
          .map(_.getValue)
          .flatMap(emptyStringAsOption)
          .getOrElse(
            s"No E-Mail for contact '${stayInTouchData.contact.vCard.getFormattedName.getValue}' found!"
          )
      )
    )

  def relevantStayInTouch(
      dbConnector: DbConnector
  )(implicit ec: ExecutionContext): Future[Vector[StayInTouchData]] = {

    dbConnector.getAllStayInTouch
      .map(_.filter(stayInTouchFilter))
      .zip(
        dbConnector.getAllContacts
          .map(_.map(contact => contact.uid -> contact).toMap)
      )
      .map { case (stayInTouches, contacts) =>
        stayInTouches.flatMap(stayInTouch =>
          contacts
            .get(stayInTouch.contactId)
            .map(
              StayInTouchData(_, stayInTouch)
            )
        )
      }

  }

  def stayInTouchFilter(stayInTouch: StayInTouch): Boolean =
    ChronoUnit.SECONDS.between(stayInTouch.lastContacted, ZonedDateTime.now())
      >= stayInTouch.contactInterval.toSeconds

}

object EmailNotifierService extends EmailNotifierService {

  sealed trait EmailNotifierServiceCmd

  // external api
  /// Run is also used for self triggering
  case object Run extends EmailNotifierServiceCmd

  // internal api

  private final case class EmailsSend(eMails: Seq[NonEmptyList[String]])
      extends EmailNotifierServiceCmd

  private final case class EmailSendFailed(ex: Throwable)
      extends EmailNotifierServiceCmd

  private[notifier] final case class StayInTouchData(
      contact: Contact,
      stayInTouch: StayInTouch
  )

  private final case class FindStayInTouchSuccessful(
      entries: Vector[StayInTouchData]
  ) extends EmailNotifierServiceCmd

  private final case class FindStayInTouchFailed(ex: Throwable)
      extends EmailNotifierServiceCmd

  // configs
  final case class EmailNotifierConfig(
      mailConfig: MailConfig,
      runHour: FiniteDuration,
      dbConnector: DbConnector
  )

  object EmailNotifierConfig {

    def apply(
        emailCfg: SimplePrmCfg.SimplePrm.EmailServer,
        notifierCfg: SimplePrmCfg.SimplePrm.Notifier,
        dbConnector: DbConnector
    ): EmailNotifierConfig =
      new EmailNotifierConfig(
        MailConfig(emailCfg),
        FiniteDuration(notifierCfg.scheduleHour.toHours, TimeUnit.HOURS),
        dbConnector
      )

  }

  object MailConfig {

    def apply(cfg: SimplePrmCfg.SimplePrm.EmailServer): emil.MailConfig =
      apply(
        s"${ConfigUtil.protocolFromConfig(cfg.protocol)}://${cfg.url}:${cfg.port}",
        cfg.user,
        cfg.password,
        mapSSLType(cfg.sslType),
        cfg.enableXOAuth2,
        cfg.disableCertificateCheck,
        FiniteDuration(cfg.timeout.toSeconds, TimeUnit.SECONDS)
      )

    def apply(
        url: String,
        user: String,
        password: String,
        sslType: emil.SSLType = emil.SSLType.SSL,
        enableXOAuth2: Boolean = false,
        disableCertificateCheck: Boolean = false,
        timeout: Duration = FiniteDuration(10, TimeUnit.SECONDS)
    ): emil.MailConfig = emil.MailConfig(
      url,
      user,
      password,
      sslType,
      enableXOAuth2,
      disableCertificateCheck,
      timeout
    )

    private def mapSSLType(cfgSSLType: SimplePrmCfg.SSLType): emil.SSLType =
      cfgSSLType match {
        case SimplePrmCfg.SSLType.ssl =>
          emil.SSLType.SSL
        case SimplePrmCfg.SSLType.starttls     => emil.SSLType.StartTLS
        case SimplePrmCfg.SSLType.noencryption => emil.SSLType.NoEncryption
      }

  }

  // state data
  final case class EmailNotifierStateData(
      config: EmailNotifierConfig,
      timers: TimerScheduler[EmailNotifierServiceCmd],
      composer: EmailComposer
  )

  def apply(
      emailNotifierConfig: EmailNotifierConfig,
      emailComposer: EmailComposer
  ): Behavior[EmailNotifierServiceCmd] =
    Behaviors.withTimers { timers =>

      // setup timers
      timers.startTimerWithFixedDelay(
        Run,
        initDelay(emailNotifierConfig.runHour.toHours),
        emailNotifierConfig.runHour
      )

      idle(EmailNotifierStateData(emailNotifierConfig, timers, emailComposer))
    }

  private def idle(
      stateData: EmailNotifierStateData
  ): Behavior[EmailNotifierServiceCmd] =
    Behaviors.receivePartial { case (ctx, Run) =>
      logger.info("Running e-mail notification service ...")
      ctx.pipeToSelf(
        relevantStayInTouch(stateData.config.dbConnector)(ctx.executionContext)
      ) {
        case Success(stayInTouches) =>
          FindStayInTouchSuccessful(stayInTouches)
        case Failure(exception) =>
          FindStayInTouchFailed(exception)
      }
      run(stateData)
    }

  private def run(
      stateData: EmailNotifierStateData
  ): Behavior[EmailNotifierServiceCmd] = Behaviors.receivePartial {
    case (ctx, FindStayInTouchSuccessful(stayInTouches)) =>
      ctx.pipeToSelf(
        sendMails(
          stateData.config.mailConfig,
          composeEmails(stateData.composer, stayInTouches)
        )(ctx.executionContext)
      ) {
        case Success(eMailStrings) =>
          EmailsSend(eMailStrings)
        case Failure(exception) =>
          EmailSendFailed(exception)
      }
      Behaviors.same
    case (_, FindStayInTouchFailed(ex)) =>
      logger.error(
        "Cannot query stay in touch entries from database. Canceling sending notification e-mails  process!",
        ex
      )
      idle(stateData)
    case (_, EmailsSend(eMails)) =>
      logger.info(s"Successfully send ${eMails.size} e-mail notifications!")
      logger.debug(s"eMails: ${eMails.toList.mkString("\n")}")
      idle(stateData)
    case (_, EmailSendFailed(ex)) =>
      logger.error("Error while sending notification e-mails.", ex)
      idle(stateData)
  }
}
