/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.test.common

import org.scalatest.Assertion
import org.scalatest.matchers.{MatchResult, Matcher, should}
import reactivemongo.api.bson.BSONValue

trait CustomMatchers {

  class BsonValueMatcher(expected: BSONValue) extends Matcher[BSONValue] {
    def apply(left: BSONValue): MatchResult = {
      MatchResult(
        left.equals(expected),
        s"Expected:\n${BSONValue.pretty(expected)}\n\nActual:\n${BSONValue.pretty(left)}",
        s"${BSONValue.pretty(left)} is equal to ${BSONValue.pretty(expected)}"
      )
    }
  }

  def beEqualPrettyPrint(expected: BSONValue) = new BsonValueMatcher(expected)

}

object CustomMatchers extends CustomMatchers
