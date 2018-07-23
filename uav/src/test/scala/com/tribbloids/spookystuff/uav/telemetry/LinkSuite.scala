package com.tribbloids.spookystuff.uav.telemetry

import com.tribbloids.spookystuff.session.Session
import com.tribbloids.spookystuff.testutils.TestHelper
import com.tribbloids.spookystuff.uav._
import com.tribbloids.spookystuff.uav.dsl.{Fleet, LinkFactory}
import com.tribbloids.spookystuff.uav.system.UAV
import com.tribbloids.spookystuff.uav.telemetry.mavlink.MAVLink
import com.tribbloids.spookystuff.utils.lifespan.Cleanable
import com.tribbloids.spookystuff.utils.{CommonUtils, SpookyUtils}
import com.tribbloids.spookystuff.{SpookyContext, SpookyEnvFixture}
import org.apache.spark.rdd.RDD

import scala.util.Random

trait LinkSuite extends UAVFixture {

  import com.tribbloids.spookystuff.utils.SpookyViews._

  override def setUp(): Unit = {
    super.setUp()
    sc.foreachComputer {
      Random.shuffle(Link.registered.values.toList).foreach(_.clean())
    }
    Thread.sleep(2000)
    // Waiting for both python drivers to terminate.
    // DON'T DELETE! some tests create proxy processes and they all take a few seconds to release the port binding!
  }

  //TODO: merge into lockedLinkRDD?
  //  def getLinkRDD(spooky: SpookyContext): RDD[Link] = {
  //    val fleet: Seq[UAV] = this.fleet
  //    val linkRDD = sc.parallelize(fleetURIs).map {
  //      connStr =>
  //        val link = spooky.withSession {
  //          session =>
  //            Dispatcher(
  //              fleet,
  //              session
  //            )
  //              .get
  //        }
  //        link.lock()
  //        TestHelper.assert(link.isReachable, "link is unreacheable")
  //        TestHelper.assert(
  //          link.factoryOpt.get == spooky.getConf[UAVConf].linkFactory,
  //          "link doesn't comply to factory"
  //        )
  //        link
  //    }
  //      .persist()
  //    val uavs = linkRDD.map(_.uav).collect()
  //    assert(uavs.length == uavs.distinct.length)
  //    linkRDD.foreach {
  //      link =>
  //        link.unlock() //TODO: delete, Selector.select unlocks automatically.
  //    }
  //    linkRDD
  //  }

  def getLinkRDD(spooky: SpookyContext) = {
    val fleet = spooky.getConf[UAVConf].get.fleet

    val linkRDD = LinkUtils.linkRDD(spooky).persist()

    val uavs = linkRDD.map(_.uav).collect()
    assert(uavs.length > 0)
    assert(uavs.length == uavs.distinct.length)
    linkRDD.foreach {
      link =>
        link.unlock()
    }
    linkRDD
  }

  def factories: Seq[LinkFactory]

  private def factory2Fixtures(factory: LinkFactory): (SpookyContext, String) = {

    val spooky = this.spooky.copy(_configurations = this.spooky.configurations.transform(_.clone))
    val uavConf = spooky.getConf[UAVConf]
    uavConf.linkFactory = factory
    uavConf.fleet = Fleet.Inventory(fleet)
    spooky.rebroadcast()

    val name = spooky.getConf[UAVConf].linkFactory.getClass.getSimpleName
    spooky -> s"linkFactory=$name"
  }

  def runTests(factories: Seq[LinkFactory])(f: SpookyContext => Unit) = {

    val fixtures: Seq[(SpookyContext, String)] = factories.map {
      factory2Fixtures
    }

    fixtures.foreach {
      case (spooky, testPrefix) =>
        describe(testPrefix) {
          f(spooky)
        }
    }
  }

  runTests(factories) {
    spooky =>

      it("Link should be registered in both Link and Cleanable") {

        for (i <- 0 to 10) {
          val linkRDD = getLinkRDD(spooky)
          val uavs = linkRDD.map(_.uav).collect()
          println(s"=== $i\t===: " + uavs.mkString("\t"))
        }

        spooky.sparkContext.foreachComputer {
          val registered = Link.registered.values.toSet
          val cleanable = Cleanable.getTyped[Link].toSet
          Predef.assert(registered.subsetOf(cleanable))
        }
      }

      it("Link should use different UAVs") {
        for (i <- 0 to 10) {
          val linkRDD = getLinkRDD(spooky)
          val uavs = linkRDD.map(_.uav).collect().toSeq
          println(s"=== $i\t===: " + uavs.mkString("\t"))

          val uris = uavs.map(_.primaryURI)
          Predef.assert(uris.size == this.parallelism, "Duplicated URIs:\n" + uris.mkString("\n"))
          Predef.assert(uris.size == uris.distinct.size, "Duplicated URIs:\n" + uris.mkString("\n"))
        }
      }

      it("Link created in the same Task should be reused") {

        val fleet = this.fleet
        val linkStrs = sc.parallelize(fleetURIs).map {
          connStr =>
            val session = new Session(spooky)
            val link1 = Dispatcher(
              fleet,
              session
            )
              .get
            val link2 = Dispatcher(
              fleet,
              session
            )
              .get
            Thread.sleep(5000) //otherwise a task will complete so fast such that another task hasn't start yet.
          val result = link1.toString -> link2.toString
            result
        }
          .collect()
        assert(spooky.getMetrics[UAVMetrics].linkCreated.value == parallelism)
        assert(spooky.getMetrics[UAVMetrics].linkDestroyed.value == 0)
        linkStrs.foreach {
          tuple =>
            assert(tuple._1 == tuple._2)
        }
      }

      for (factory2 <- factories) {

        it(
          s"~> ${factory2.getClass.getSimpleName}:" +
            s" available Link can be recommissioned in another Task"
        ) {

          val factory1 = spooky.getConf[UAVConf].linkFactory

          val linkRDD1: RDD[Link] = getLinkRDD(spooky)

          spooky.getConf[UAVConf].linkFactory = factory2
          spooky.rebroadcast()

          try {

            assert(spooky.getMetrics[UAVMetrics].linkCreated.value == parallelism)
            assert(spooky.getMetrics[UAVMetrics].linkDestroyed.value == 0)

            val linkRDD2: RDD[Link] = getLinkRDD(spooky)

            if (factory1 == factory2) {
              assert(spooky.getMetrics[UAVMetrics].linkCreated.value == parallelism)
              assert(spooky.getMetrics[UAVMetrics].linkDestroyed.value == 0)
              linkRDD1.map(_.toString).collect().mkString("\n").shouldBe(
                linkRDD2.map(_.toString).collect().mkString("\n"),
                sort = true
              )
            }
            else {
              assert(spooky.getMetrics[UAVMetrics].linkCreated.value == parallelism)
              // TODO: should be parallelism*2!
              assert(spooky.getMetrics[UAVMetrics].linkDestroyed.value == 0)
              linkRDD1.map(_.uav).collect().mkString("\n").shouldBe(
                linkRDD2.map(_.uav).collect().mkString("\n"),
                sort = true
              )
            }
          }
          finally {
            spooky.getConf[UAVConf].linkFactory = factory1
            spooky.rebroadcast()
          }
        }
      }
  }
}

abstract class SimLinkSuite extends SimUAVFixture with LinkSuite {

  import com.tribbloids.spookystuff.utils.SpookyViews._

  runTests(factories) {
    spooky =>

      it("Link to unreachable drone should be disabled until blacklist timer reset") {
        val session = new Session(spooky)
        val drone = UAV(Seq("dummy"))
        TestHelper.setLoggerDuring(classOf[Link], classOf[MAVLink], SpookyUtils.getClass) {
          intercept[LinkDepletedException] {
            Dispatcher(
              Seq(drone),
              session
            )
              .get
          }

          val badLink = Link.registered(drone)
          assert(badLink.statusStr.contains("DRONE@dummy -> unreachable for"))
          //          assert {
          //            val e = badLink.lastFailureOpt.get._1
          //            e.isInstanceOf[AssertionError] //|| e.isInstanceOf[Wrap]
          //          }
        }
      }

      it("Link.connect()/disconnect() should not leave dangling process") {
        val linkRDD: RDD[Link] = getLinkRDD(spooky)
        linkRDD.foreach {
          link =>
            for (_ <- 1 to 2) {
              link.connect()
              link.disconnect()
            }
        }
        //wait for zombie process to be deregistered
        CommonUtils.retry(5, 2000) {
          sc.foreachComputer {
            SpookyEnvFixture.processShouldBeClean(Seq("mavproxy"), Seq("mavproxy"), cleanSweepNotInTask = false)

            Link.registered.foreach {
              v => v._2.disconnect()
            }
          }
        }
      }
  }
}
