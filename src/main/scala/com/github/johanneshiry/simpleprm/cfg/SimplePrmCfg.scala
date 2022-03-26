/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.cfg

final case class SimplePrmCfg(
    simple_prm: SimplePrmCfg.SimplePrm
)
object SimplePrmCfg {
  final case class MongoDB(
      authenticationDb: scala.Option[java.lang.String],
      host: java.lang.String,
      password: scala.Option[java.lang.String],
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
        user = $_reqStr(parentPath, c, "user", $tsCfgValidator)
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

  final case class SimplePrm(
      carddav: SimplePrmCfg.SimplePrm.Carddav,
      database: SimplePrmCfg.MongoDB
  )
  object SimplePrm {
    final case class Carddav(
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
          password = $_reqStr(parentPath, c, "password", $tsCfgValidator),
          syncInterval =
            if (c.hasPathOrNull("syncInterval")) c.getDuration("syncInterval")
            else java.time.Duration.parse("PT0.015S"),
          uri = $_reqStr(parentPath, c, "uri", $tsCfgValidator),
          username = $_reqStr(parentPath, c, "username", $tsCfgValidator)
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
