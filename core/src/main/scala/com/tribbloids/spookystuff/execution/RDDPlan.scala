package com.tribbloids.spookystuff.execution

import com.tribbloids.spookystuff.SpookyContext
import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.row.{BeaconRDD, SpookySchema, SquashedFetchedRDD}
import org.apache.spark.rdd.RDD

import scala.collection.mutable.ArrayBuffer

/**
  * Basic Plan with no children, isExecuted always= true
  */
case class RDDPlan(
    sourceRDD: SquashedFetchedRDD,
    override val schema: SpookySchema,
    override val spooky: SpookyContext,
    beaconRDD: Option[BeaconRDD[TraceView]] = None,
    @transient override val scratchRDDs: ScratchRDDs = ScratchRDDs()
) extends ExecutionPlan(
      Seq(),
      SpookyExecutionContext(spooky, scratchRDDs)
    ) {

  override lazy val beaconRDDOpt = beaconRDD

  override def doExecute(): SquashedFetchedRDD = sourceRDD
}
