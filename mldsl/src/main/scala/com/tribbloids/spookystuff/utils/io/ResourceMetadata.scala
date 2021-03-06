package com.tribbloids.spookystuff.utils.io

import org.apache.spark.ml.dsl.utils.data.{EAV, EAVRelay, EAVView}

case class ResourceMetadata(
    override val source: EAV.Impl = EAV.empty
) extends EAVView {

  type VV = Any
  override def ctg = getCtg

  object uri extends Attr[String]()
  object name extends Attr[String]()
  object `type` extends Attr[String]()
  object `content-type` extends Attr[String]()
  object length extends Attr[Long]()
  object `status-code` extends Attr[Int]()

  object `isDir` extends Attr[Boolean]()
}

object ResourceMetadata extends EAVRelay[ResourceMetadata] {
  override def fromCore(v: EAV.Impl): ResourceMetadata = ResourceMetadata(v)
}
