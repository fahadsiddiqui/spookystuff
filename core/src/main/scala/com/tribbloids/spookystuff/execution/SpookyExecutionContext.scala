package com.tribbloids.spookystuff.execution

import com.tribbloids.spookystuff.SpookyContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
  * Created by peng on 10/07/17.
  */
//TODO: merge into DataRowSchema?
case class SpookyExecutionContext(
    spooky: SpookyContext,
    @transient scratchRDDs: ScratchRDDs = ScratchRDDs()
) {

  def :++(b: SpookyExecutionContext) = {
    //    assert(this.spooky == b.spooky,
    //      "cannot merge execution plans due to diverging SpookyContext")

    import scratchRDDs._
    val bb = b.scratchRDDs
    this.copy(
      scratchRDDs = ScratchRDDs(
        tempTables = <+>(bb, _.tempTables),
        tempRDDs = <+>(bb, _.tempRDDs),
        tempDSs = <+>(bb, _.tempDSs)
      )
    )
  }

  def persist[T](
      rdd: RDD[T],
      storageLevel: StorageLevel = spooky.spookyConf.defaultStorageLevel
  ): RDD[T] = {

    scratchRDDs.persist(rdd, storageLevel)
  }
}
