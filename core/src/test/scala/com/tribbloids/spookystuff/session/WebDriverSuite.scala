package com.tribbloids.spookystuff.session

import java.util.Date

import com.tribbloids.spookystuff.SpookyEnvFixture
import com.tribbloids.spookystuff.actions.{Visit, Wget}
import com.tribbloids.spookystuff.testutils.LocalPathDocsFixture
import org.apache.spark.SparkException

/**
  * Created by peng on 24/11/16.
  */
class WebDriverSuite extends SpookyEnvFixture with LocalPathDocsFixture {

  import com.tribbloids.spookystuff.dsl._

  it("PhantomJS DriverFactory can degrade gracefully if remote URI is unreachable") {

    this.spookyConf.webDriverFactory = DriverFactories.PhantomJS(
      getLocalURI = _ => "dummy/file",
      getRemoteURI = _ => "dummy.org/file",
      redeploy = true
    )
    this.spookyConf.IgnoreCachedDocsBefore = Some(new Date())
    try {

      reloadSpooky

      spooky
        .create(1 to 2)
        .fetch(
          Wget(HTML_URL)
        )
        .count()

      intercept[SparkException] {
        val docs = spooky
          .create(1 to 2)
          .fetch(
            Visit(HTML_URL)
          )
          .docRDD

        docs.collect().foreach(println)
      }
    } finally {
      this.spookyConf.webDriverFactory = DriverFactories.PhantomJS()
      reloadSpooky
    }
  }
}
