package com.tribbloids.spookystuff.uav.sim

import com.tribbloids.spookystuff.session.python.{CaseInstanceRef, SingletonRef}
import com.tribbloids.spookystuff.utils.CachingUtils
import com.tribbloids.spookystuff.utils.lifespan.{Lifespan, LocalCleanable}

/**
  * Created by peng on 27/10/16.
  */
object APMSim {

  val existing: CachingUtils.ConcurrentSet[APMSim] = CachingUtils.ConcurrentSet()

  final val FRAMERATE = 200
  final val SPEEDUP = 5

  def next(
            extraArgs: Seq[String],
            vType: String = "copter",
            version: String = "3.3"
          ): APMSim = this.synchronized {
    val nextINumOpt = (0 to 254).find{
      i =>
        !existing.map(_.iNum).toSeq.contains(i)
    }
    val nextINum = nextINumOpt
      .getOrElse(
        throw new UnsupportedOperationException("APMSim iNum depleted")
      )
    APMSim(nextINum, extraArgs = extraArgs, vType = vType, version = version)
  }
}

case class APMSim private (
                            iNum: Int,
                            extraArgs: Seq[String],
                            rate: Int = APMSim.FRAMERATE,
                            speedup: Int = APMSim.SPEEDUP,
                            vType: String = "copter",
                            version: String = "3.3"
                          ) extends CaseInstanceRef with SingletonRef with LocalCleanable {

  APMSim.existing += this

  override def cleanImpl() = {
    super.cleanImpl()
    APMSim.existing -= this
  }
}