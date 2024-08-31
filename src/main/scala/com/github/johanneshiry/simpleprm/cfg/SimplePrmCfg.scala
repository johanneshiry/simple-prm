/*
 * © 2024. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.cfg

final case class SimplePrmCfg(
    simple_prm: SimplePrmCfg.SimplePrm
)
object SimplePrmCfg {
  sealed trait MailProtocol
  object MailProtocol {
    object smtp extends MailProtocol
    def $resEnum(
        name: java.lang.String,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): MailProtocol = name match {
      case "smtp" => MailProtocol.smtp
      case v =>
        $tsCfgValidator.addInvalidEnumValue(path, v, "MailProtocol")
        null
    }
  }
  final case class MongoDB(
      authenticationDb: scala.Option[java.lang.String],
      host: java.lang.String,
      password: scala.Option[java.lang.String],
      port: scala.Int,
      user: java.lang.String
  )
  object MongoDB {
    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): SimplePrmCfg.MongoDB = {
      SimplePrmCfg.MongoDB(
        authenticationDb =
          if (c.hasPathOrNull("authenticationDb"))
            Some(c.getString("authenticationDb"))
          else None,
        host = $_reqStr(parentPath, c, "host", $tsCfgValidator),
        password =
          if (c.hasPathOrNull("password")) Some(c.getString("password"))
          else None,
        port = $_reqInt(parentPath, c, "port", $tsCfgValidator),
        user = $_reqStr(parentPath, c, "user", $tsCfgValidator)
      )
    }
    private def $_reqInt(
        parentPath: java.lang.String,
        c: com.typesafe.config.Config,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): scala.Int = {
      if (c == null) 0
      else
        try c.getInt(path)
        catch {
          case e: com.typesafe.config.ConfigException =>
            $tsCfgValidator.addBadPath(parentPath + path, e)
            0
        }
    }

    private def $_reqStr(
        parentPath: java.lang.String,
        c: com.typesafe.config.Config,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): java.lang.String = {
      if (c == null) null
      else
        try c.getString(path)
        catch {
          case e: com.typesafe.config.ConfigException =>
            $tsCfgValidator.addBadPath(parentPath + path, e)
            null
        }
    }

  }

  sealed trait SSLType
  object SSLType {
    object ssl extends SSLType
    object starttls extends SSLType
    object noencryption extends SSLType
    def $resEnum(
        name: java.lang.String,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): SSLType = name match {
      case "ssl"          => SSLType.ssl
      case "starttls"     => SSLType.starttls
      case "noencryption" => SSLType.noencryption
      case v =>
        $tsCfgValidator.addInvalidEnumValue(path, v, "SSLType")
        null
    }
  }
  final case class SimplePrm(
      carddav: SimplePrmCfg.SimplePrm.Carddav,
      database: SimplePrmCfg.MongoDB,
      emailServer: SimplePrmCfg.SimplePrm.EmailServer,
      notifier: SimplePrmCfg.SimplePrm.Notifier,
      rest: SimplePrmCfg.SimplePrm.Rest
  )
  object SimplePrm {
    final case class Carddav(
        disableCertificateCheck: scala.Boolean,
        password: java.lang.String,
        syncInterval: java.time.Duration,
        uri: java.lang.String,
        username: java.lang.String
    )
    object Carddav {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): SimplePrmCfg.SimplePrm.Carddav = {
        SimplePrmCfg.SimplePrm.Carddav(
          disableCertificateCheck =
            $_reqBln(parentPath, c, "disableCertificateCheck", $tsCfgValidator),
          password = $_reqStr(parentPath, c, "password", $tsCfgValidator),
          syncInterval =
            if (c.hasPathOrNull("syncInterval")) c.getDuration("syncInterval")
            else java.time.Duration.parse("PT0.015S"),
          uri = $_reqStr(parentPath, c, "uri", $tsCfgValidator),
          username = $_reqStr(parentPath, c, "username", $tsCfgValidator)
        )
      }
      private def $_reqBln(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Boolean = {
        if (c == null) false
        else
          try c.getBoolean(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              false
          }
      }

      private def $_reqStr(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): java.lang.String = {
        if (c == null) null
        else
          try c.getString(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              null
          }
      }

    }

    final case class EmailServer(
        disableCertificateCheck: scala.Boolean,
        enableXOAuth2: scala.Boolean,
        password: java.lang.String,
        port: scala.Int,
        protocol: SimplePrmCfg.MailProtocol,
        sslType: SimplePrmCfg.SSLType,
        timeout: java.time.Duration,
        url: java.lang.String,
        user: java.lang.String
    )
    object EmailServer {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): SimplePrmCfg.SimplePrm.EmailServer = {
        SimplePrmCfg.SimplePrm.EmailServer(
          disableCertificateCheck =
            $_reqBln(parentPath, c, "disableCertificateCheck", $tsCfgValidator),
          enableXOAuth2 =
            $_reqBln(parentPath, c, "enableXOAuth2", $tsCfgValidator),
          password = $_reqStr(parentPath, c, "password", $tsCfgValidator),
          port = $_reqInt(parentPath, c, "port", $tsCfgValidator),
          protocol = SimplePrmCfg.MailProtocol.$resEnum(
            c.getString("protocol"),
            parentPath + "protocol",
            $tsCfgValidator
          ),
          sslType = SimplePrmCfg.SSLType.$resEnum(
            c.getString("sslType"),
            parentPath + "sslType",
            $tsCfgValidator
          ),
          timeout =
            if (c.hasPathOrNull("timeout")) c.getDuration("timeout")
            else java.time.Duration.parse("PT0.01S"),
          url = $_reqStr(parentPath, c, "url", $tsCfgValidator),
          user = $_reqStr(parentPath, c, "user", $tsCfgValidator)
        )
      }
      private def $_reqBln(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Boolean = {
        if (c == null) false
        else
          try c.getBoolean(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              false
          }
      }

      private def $_reqInt(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Int = {
        if (c == null) 0
        else
          try c.getInt(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              0
          }
      }

      private def $_reqStr(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): java.lang.String = {
        if (c == null) null
        else
          try c.getString(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              null
          }
      }

    }

    final case class Notifier(
        email: SimplePrmCfg.SimplePrm.Notifier.Email,
        globalBirthdayEnabled: scala.Boolean,
        scheduleHour: java.time.Duration
    )
    object Notifier {
      final case class Email(
          receiver: java.lang.String,
          sender: java.lang.String
      )
      object Email {
        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): SimplePrmCfg.SimplePrm.Notifier.Email = {
          SimplePrmCfg.SimplePrm.Notifier.Email(
            receiver = $_reqStr(parentPath, c, "receiver", $tsCfgValidator),
            sender = $_reqStr(parentPath, c, "sender", $tsCfgValidator)
          )
        }
        private def $_reqStr(
            parentPath: java.lang.String,
            c: com.typesafe.config.Config,
            path: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): java.lang.String = {
          if (c == null) null
          else
            try c.getString(path)
            catch {
              case e: com.typesafe.config.ConfigException =>
                $tsCfgValidator.addBadPath(parentPath + path, e)
                null
            }
        }

      }

      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): SimplePrmCfg.SimplePrm.Notifier = {
        SimplePrmCfg.SimplePrm.Notifier(
          email = SimplePrmCfg.SimplePrm.Notifier.Email(
            if (c.hasPathOrNull("email")) c.getConfig("email")
            else com.typesafe.config.ConfigFactory.parseString("email{}"),
            parentPath + "email.",
            $tsCfgValidator
          ),
          globalBirthdayEnabled =
            $_reqBln(parentPath, c, "globalBirthdayEnabled", $tsCfgValidator),
          scheduleHour =
            if (c.hasPathOrNull("scheduleHour")) c.getDuration("scheduleHour")
            else java.time.Duration.parse("PT0.009S")
        )
      }
      private def $_reqBln(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Boolean = {
        if (c == null) false
        else
          try c.getBoolean(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              false
          }
      }

    }

    final case class Rest(
        host: java.lang.String,
        port: scala.Int
    )
    object Rest {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): SimplePrmCfg.SimplePrm.Rest = {
        SimplePrmCfg.SimplePrm.Rest(
          host = $_reqStr(parentPath, c, "host", $tsCfgValidator),
          port = $_reqInt(parentPath, c, "port", $tsCfgValidator)
        )
      }
      private def $_reqInt(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Int = {
        if (c == null) 0
        else
          try c.getInt(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              0
          }
      }

      private def $_reqStr(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): java.lang.String = {
        if (c == null) null
        else
          try c.getString(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              null
          }
      }

    }

    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): SimplePrmCfg.SimplePrm = {
      SimplePrmCfg.SimplePrm(
        carddav = SimplePrmCfg.SimplePrm.Carddav(
          if (c.hasPathOrNull("carddav")) c.getConfig("carddav")
          else com.typesafe.config.ConfigFactory.parseString("carddav{}"),
          parentPath + "carddav.",
          $tsCfgValidator
        ),
        database = SimplePrmCfg.MongoDB(
          if (c.hasPathOrNull("database")) c.getConfig("database")
          else com.typesafe.config.ConfigFactory.parseString("database{}"),
          parentPath + "database.",
          $tsCfgValidator
        ),
        emailServer = SimplePrmCfg.SimplePrm.EmailServer(
          if (c.hasPathOrNull("emailServer")) c.getConfig("emailServer")
          else com.typesafe.config.ConfigFactory.parseString("emailServer{}"),
          parentPath + "emailServer.",
          $tsCfgValidator
        ),
        notifier = SimplePrmCfg.SimplePrm.Notifier(
          if (c.hasPathOrNull("notifier")) c.getConfig("notifier")
          else com.typesafe.config.ConfigFactory.parseString("notifier{}"),
          parentPath + "notifier.",
          $tsCfgValidator
        ),
        rest = SimplePrmCfg.SimplePrm.Rest(
          if (c.hasPathOrNull("rest")) c.getConfig("rest")
          else com.typesafe.config.ConfigFactory.parseString("rest{}"),
          parentPath + "rest.",
          $tsCfgValidator
        )
      )
    }
  }

  def apply(c: com.typesafe.config.Config): SimplePrmCfg = {
    val $tsCfgValidator: $TsCfgValidator = new $TsCfgValidator()
    val parentPath: java.lang.String = ""
    val $result = SimplePrmCfg(
      simple_prm = SimplePrmCfg.SimplePrm(
        if (c.hasPathOrNull("simple-prm")) c.getConfig("simple-prm")
        else com.typesafe.config.ConfigFactory.parseString("simple-prm{}"),
        parentPath + "simple-prm.",
        $tsCfgValidator
      )
    )
    $tsCfgValidator.validate()
    $result
  }
  final class $TsCfgValidator {
    private val badPaths =
      scala.collection.mutable.ArrayBuffer[java.lang.String]()

    def addBadPath(
        path: java.lang.String,
        e: com.typesafe.config.ConfigException
    ): Unit = {
      badPaths += s"'$path': ${e.getClass.getName}(${e.getMessage})"
    }

    def addInvalidEnumValue(
        path: java.lang.String,
        value: java.lang.String,
        enumName: java.lang.String
    ): Unit = {
      badPaths += s"'$path': invalid value $value for enumeration $enumName"
    }

    def validate(): Unit = {
      if (badPaths.nonEmpty) {
        throw new com.typesafe.config.ConfigException(
          badPaths.mkString("Invalid configuration:\n    ", "\n    ", "")
        ) {}
      }
    }
  }
}
