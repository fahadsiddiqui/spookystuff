package com.tribbloids.spookystuff.testutils

import org.apache.spark.ml.dsl.utils.OptionConversion
import org.json4s.JValue
import org.json4s.jackson.JsonMethods
import org.scalatest.{FunSpec, Suite}

/**
  * Created by peng on 17/05/16.
  */
trait Suitex extends OptionConversion {
  self: Suite =>

  final val ACTUAL =
    "[ACTUAL   /  LEFT]"
  final val EXPECTED =
    "[EXPECTED / RIGHT]"

//  CommonUtils.debugCPResource()

  @transient implicit class TestStringView(str: String) {

    //TODO: use reflection to figure out test name and annotate
    def shouldBe(
        gd: String = null,
        sort: Boolean = false,
        ignoreCase: Boolean = false,
        superSet: Boolean = false
    ): Unit = {

      var a: List[String] = str
        .split("\n")
        .toList
        .filterNot(_.replaceAllLiterally(" ", "").isEmpty)
        .map(v => ("|" + v).trim.stripPrefix("|"))
      if (sort) a = a.sorted
      if (ignoreCase) a = a.map(_.toLowerCase)

      Option(gd) match {
        case None =>
          println(AssertionErrorObject(a, null).actualInfo)
        case Some(_gd) =>
          var b = _gd
            .split("\n")
            .toList
            .filterNot(_.replaceAllLiterally(" ", "").isEmpty)
            .map(v => ("|" + v).trim.stripPrefix("|"))
          if (sort) b = b.sorted
          if (ignoreCase) b = b.map(_.toLowerCase)
          if (superSet) {
            TestHelper.assert(
              a.intersect(b).nonEmpty,
              AssertionErrorObject(a, b)
            )
          } else {
            TestHelper.assert(
              a == b,
              AssertionErrorObject(a, b)
            )
          }
      }
    }

    //ignore sequence
//    def jsonShouldBe(
//        gd: String = null,
//        mode: JSONCompareMode = JSONCompareMode.LENIENT
//    ): Unit = {
//
//      JSONAssert.assertEquals(str, gd, mode)
//    }

    def rowsShouldBe(
        gd: String = null
    ) = shouldBe(gd, sort = true)

    def shouldBeLike(
        gd: String = null,
        sort: Boolean = false,
        ignoreCase: Boolean = false
    ): Unit = {
      val aRaw: List[String] = str
        .split("\n")
        .toList
        .filterNot(_.replaceAllLiterally(" ", "").isEmpty)
        .map(v => ("|" + v).trim.stripPrefix("|"))
      val a =
        if (sort) aRaw.sorted
        else aRaw

      Option(gd) match {
        case None =>
          println(AssertionErrorObject(a, null).actualInfo)
        case Some(_gd) =>
          var b = _gd
            .split("\n")
            .toList
            .filterNot(_.replaceAllLiterally(" ", "").isEmpty)
            .map(v => ("|" + v).trim.stripPrefix("|"))
          if (sort) b = b.sorted
          if (ignoreCase) b = b.map(_.toLowerCase)
          try {
            a.zipAll(b, null, null).foreach { tuple =>
              val fixes = tuple._2.split("[\\.]{6,}", 2)
              TestHelper.assert(
                tuple._1.startsWith(fixes.head)
              )
              TestHelper.assert(
                tuple._1.endsWith(fixes.last)
              )
            }
          } catch {
            case e: Throwable =>
              throw new AssertionError("" + AssertionErrorObject(a, b), e)
          }
      }
    }

    def rowsShouldBeLike(gd: String = null): Unit = shouldBeLike(gd, sort = true)

    //    def uriContains(contains: String): Boolean = {
    //      str.contains(contains) &&
    //        str.contains(URLEncoder.encode(contains,"UTF-8"))
    //    }
    //
    //    def assertUriContains(contains: String): Unit = {
    //      assert(
    //        str.contains(contains) &&
    //        str.contains(URLEncoder.encode(contains,"UTF-8")),
    //        s"$str doesn't contain either:\n" +
    //          s"$contains OR\n" +
    //          s"${URLEncoder.encode(contains,"UTF-8")}"
    //      )
    //    }

    def jsonShouldBe(gd: String): Unit = {
      val selfJ = JsonMethods.parse(str)
      val gdJ = JsonMethods.parse(gd)

      assertValidDataInJson(selfJ, gdJ)
    }

    // from org.apache.spark.JsonTestUtils
    def assertValidDataInJson(validateJson: JValue, expectedJson: JValue) {

      import org.json4s._

      val Diff(c, a, d) = validateJson.diff(expectedJson)
      val validatePretty = JsonMethods.pretty(validateJson)
      val expectedPretty = JsonMethods.pretty(expectedJson)
      val errorMessage = s"Expected:\n$expectedPretty\nFound:\n$validatePretty"
      import org.scalactic.TripleEquals._
      assert(c == JNothing, s"$errorMessage\nChanged:\n${JsonMethods.pretty(c)}")
      assert(a == JNothing, s"$errorMessage\nAdded:\n${JsonMethods.pretty(a)}")
      assert(d == JNothing, s"$errorMessage\nDeleted:\n${JsonMethods.pretty(d)}")
    }
  }

  //TODO: update to be on par with scalatest supported by IDE
  case class AssertionErrorObject(actual: List[String], expected: List[String]) {

    lazy val actualInfo = s"=============================== $ACTUAL ================================\n" +
      actual.mkString("\n") + "\n"

    override def toString = {
      val toBePrinted =
        s"\n=============================== $EXPECTED ================================\n" +
          expected.mkString("\n") + "\n" +
          actualInfo

      println(toBePrinted)

      s"""
         |"
         |${actual.mkString("\n")}
         |" did not equal "
         |${expected.mkString("\n")}
         |"
      """.trim.stripMargin
    }
  }

  @transient implicit class TestMapView[K, V](map: scala.collection.Map[K, V]) {

    assert(map != null)

    def shouldBe(expected: scala.collection.Map[K, V]): Unit = {

      val messages = expected.toSeq.flatMap { tuple =>
        val messageOpt = map.get(tuple._1) match {
          case None =>
            Some(s"${tuple._1} doesn't exist in map")
          case Some(v) =>
            if (v == tuple._2) None
            else Some(s"${tuple._1} mismatch: expected ${tuple._2} =/= actual $v")
        }
        messageOpt
      }

      if (messages.nonEmpty)
        throw new AssertionError("Assertion failure: {\n" + messages.mkString("\n") + "\n}")
    }

    def shouldBe(expected: (K, V)*): Unit = {
      this.shouldBe(Map(expected: _*))
    }
  }

  def printSplitter(name: String) = {
    println(s"======================================= $name ===================================")
  }

  def bypass(f: => Unit) = {}

  //  override def intercept[T <: AnyRef](f: => Any)(implicit manifest: Manifest[T]): T = {
  //    super.intercept{
  //      try f
  //      catch {
  //        case e: Throwable =>
  //          println("Attempt to intercept:")
  //          e.printStackTrace()
  //          throw e
  //      }
  //    }
  //  }
}

trait FunSpecx extends FunSpec with Suitex {}
