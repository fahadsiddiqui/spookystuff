package org.apache.spark.ml.dsl.utils.refl

import java.nio.ByteBuffer

import org.apache.spark.SparkConf
import org.apache.spark.serializer.JavaSerializer
import org.apache.spark.sql.types._

import scala.language.{existentials, implicitConversions}

/**
  *  A Scala TypeTag-based UDT, by default it doesn't compress object
  *  ideally it should compress object into InternalRow.
  *  Should be working and serve as the fallback strategy for ScalaReflection.schemaFor
  */
abstract class ScalaUDT[T >: Null](implicit val self: ScalaType[T])
    extends UserDefinedType[T]
    with ScalaType.AsCatalystType[T] {

  override val typeName: String = this.getClass.getSimpleName.stripSuffix("$") //.stripSuffix("Type").stripSuffix("UDT").toLowerCase

  def serDe: JavaSerializer = { //TODO: kryo is better
    val conf = new SparkConf()
    new JavaSerializer(conf)
  }

  def sqlType: DataType = BinaryType

  override def userClass: Class[T] = {
    self.asClassTag.runtimeClass.asInstanceOf[Class[T]]
  }

  //should convert to internal Row.
  override def serialize(obj: T): Any = {
    serDe.newInstance().serialize(obj)(self.asClassTag).array()
  }

  override def deserialize(datum: Any): T = {
    datum match {
      case a: Array[Byte] =>
        serDe.newInstance().deserialize[T](ByteBuffer.wrap(a))(self.asClassTag)
    }
  }
}
