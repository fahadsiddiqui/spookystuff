package org.apache.spark.ml.dsl.utils.refl

import java.sql.{Date, Timestamp}

import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.catalyst.ScalaReflection.universe._
import org.apache.spark.sql.types._

import scala.collection.Map
import scala.language.{existentials, implicitConversions}
import scala.reflect.ClassTag

/**
  * interface that unifies TypeTag, ClassTag, Class & DataType
  * Also a subclass of Spark SQL DataType but NOT recommended to use directly in DataFrame, can cause compatibility issues.
  * either use tryReify to attempt converting to a native DataType. Or use UnoptimizedScalaUDT (which is abandoned in Spark 2.x)
  * Will be simplified again once Spark 2.2 introduces UserDefinedType V2.
  */
//TODO: change to ThreadLocal to bypass thread safety?
//TODO: this should be a codec
trait ScalaType[T] extends ReflectionLock with Serializable {

  override def toString = asType.toString

  @transient lazy val mirror: Mirror = asTypeTag.mirror

  @transient final lazy val asTypeTag: TypeTag[T] = locked {
    _typeTag
  }

  def _typeTag: TypeTag[T]

  @transient lazy val asType: Type = locked {
    ScalaReflection.localTypeOf(asTypeTag)
  }
  @transient lazy val asClass: Class[T] = locked {
    val result = mirror.runtimeClass(asType).asInstanceOf[Class[T]]
    result
  }
  @transient lazy val asClassTag: ClassTag[T] = locked {
    ClassTag(asClass)
  }

  @transient lazy val tryReify: scala.util.Try[DataType] = locked {
    TypeUtils
      .tryCatalystTypeFor(asTypeTag)
  }

  def reify: DataType = tryReify.get

  def asCatalystType = tryReify.getOrElse {
    new UnreifiedObjectType[T]()(this)
  }

  //  def reifyOrNullType = tryReify.getOrElse { NullType }

  // see [SPARK-8647], this achieves the needed constant hash code without declaring singleton
  final override def hashCode: Int = asClass.hashCode()

  final override def equals(v: Any): Boolean = {
    if (v == null) return false
    v match {
      case vv: ScalaType[_] =>
        (this.asClass == vv.asClass) && (this.asType =:= vv.asType)
      case _ =>
        false
    }
  }

  object utils {

    lazy val companionObject: Any = {
      val mirror = ScalaType.this.mirror
      val companionMirror = mirror.reflectModule(asType.typeSymbol.companion.asModule)
      companionMirror.instance
    }

    lazy val baseCompanionObjects: Seq[Any] = {

      val mirror = ScalaType.this.mirror
      val supers = asType.typeSymbol.asClass.baseClasses

      supers.flatMap { ss =>
        scala.util.Try {
          val companionMirror = mirror.reflectModule(ss.companion.asModule)
          companionMirror.instance
        }.toOption
      }
    }
  }
}

abstract class ScalaType_Level1 {

  trait Clz[T] extends ScalaType[T] {

    def _class: Class[T]
    override lazy val asClass = {
      _class
    }

    @transient override lazy val mirror = {
      val loader = _class.getClassLoader
      runtimeMirror(loader)
    }
    //    def mirror = ReflectionUtils.mirrorFactory.get()

    @transient override lazy val asType = locked {
      //      val name = _class.getCanonicalName
      val classSymbol = mirror.classSymbol(_class)
      val tpe = classSymbol.selfType
      tpe
    }

    override def _typeTag: TypeTag[T] = {
      TypeUtils.createTypeTag(asType, mirror)
    }
  }

  implicit class FromClass[T](val _class: Class[T]) extends Clz[T]
  implicit def fromClass[T](implicit v: Class[T]) = new FromClass(v)
}

abstract class ScalaType_Level2 extends ScalaType_Level1 {

  trait Ctg[T] extends Clz[T] {

    def _classTag: ClassTag[T]
    @transient override lazy val asClassTag = {
      _classTag
    }

    def _class: Class[T] = locked {
      _classTag.runtimeClass.asInstanceOf[Class[T]]
    }
  }
  implicit class FromClassTag[T](val _classTag: ClassTag[T]) extends Ctg[T]
  implicit def fromClassTag[T](implicit v: ClassTag[T]) = new FromClassTag(v)
}

object ScalaType extends ScalaType_Level2 {

  trait Ttg[T] extends ScalaType[T] {}
  implicit class FromTypeTag[T](override val _typeTag: TypeTag[T]) extends Ttg[T]
  implicit def fromTypeTag[T](implicit v: TypeTag[T]) = new FromTypeTag(v)

  def summon[T](implicit ev: ScalaType[T]): ScalaType[T] = ev

  def getRuntimeType(v: Any): ScalaType[_] = {
    v match {
      case v: RuntimeTypeOverride => v.runtimeType
      case _                      => v.getClass
    }
  }

  object FromCatalystType {

    lazy val atomicExamples: Seq[(Any, TypeTag[_])] = {

      implicit def pairFor[T: TypeTag](v: T): (T, TypeTag[T]) = {
        v -> TypeUtils.getTypeTag[T](v)
      }

      val result = Seq[(Any, TypeTag[_])](
        Array(0: Byte),
        false,
        new Date(0),
        new Timestamp(0),
        0.0,
        0: Float,
        0: Byte,
        0: Int,
        0L,
        0: Short,
        "a"
      )
      result
    }

    lazy val atomicTypePairs: Seq[(DataType, TypeTag[_])] = atomicExamples.map { v =>
      ScalaType.fromTypeTag(v._2).tryReify.get -> v._2
    }

    lazy val atomicTypeMap: Map[DataType, TypeTag[_]] = {
      Map(atomicTypePairs: _*)
    }
  }

  implicit class FromCatalystType(tt: DataType) extends ReflectionLock {

    // CatalystType => ScalaType
    // used in ReflectionMixin to determine the exact function to:
    // 1. convert data from CatalystType to canonical Scala Type (and obtain its TypeTag)
    // 2. use the obtained TypeTag to get the specific function implementation and applies to the canonic Scala Type data.
    // 3. get the output TypeTag of the function, use it to generate the output DataType of the new Extraction.
    def typeTagOpt: Option[TypeTag[_]] = locked {

      tt match {
        case NullType =>
          Some(TypeTag.Null)
        case st: ScalaType.AsCatalystType[_] =>
          Some(st.self.asTypeTag)
        case t if FromCatalystType.atomicTypeMap.contains(t) =>
          FromCatalystType.atomicTypeMap.get(t)
        case ArrayType(inner, _) =>
          val innerTagOpt = inner.typeTagOpt
          innerTagOpt.map {
            case at: TypeTag[a] =>
              implicit val att = at
              typeTag[Array[a]]
          }
        case MapType(key, value, _) =>
          val keyTag = key.typeTagOpt
          val valueTag = value.typeTagOpt
          val pairs = (keyTag, valueTag) match {
            case (Some(kt), Some(vt)) => Some(kt -> vt)
            case _                    => None
          }

          pairs.map { pair =>
            (pair._1, pair._2) match {
              case (ttg1: TypeTag[a], ttg2: TypeTag[b]) =>
                implicit val t1 = ttg1
                implicit val t2 = ttg2
                typeTag[Map[a, b]]
            }
          }
        case _ =>
          None
      }
    }

    def asTypeTag: TypeTag[_] = {
      typeTagOpt.getOrElse {
        throw new UnsupportedOperationException(
          s"cannot convert Catalyst type $tt to Scala type: TypeTag=${tt.typeTagOpt}")
      }
    }

    def asTypeTagCasted[T]: TypeTag[T] = asTypeTag.asInstanceOf[TypeTag[T]]

    @transient lazy val reified: DataType = locked {
      val result = UnreifiedObjectType.reify(tt)
      result
    }

    def unboxArrayOrMap: DataType = locked {
      tt._unboxArrayOrMapOpt
        .orElse(
          tt.reified._unboxArrayOrMapOpt
        )
        .getOrElse(
          throw new UnsupportedOperationException(s"Type $tt is not an Array")
        )
    }

    private[utils] def _unboxArrayOrMapOpt: Option[DataType] = locked {
      tt match {
        case ArrayType(boxed, _) =>
          Some(boxed)
        case MapType(keyType, valueType, valueContainsNull) =>
          Some(
            StructType(
              Array(
                StructField("_1", keyType),
                StructField("_2", valueType, valueContainsNull)
              )))
        case _ =>
          None
      }
    }

    def filterArray: Option[DataType] = locked {
      if (tt.reified.isInstanceOf[ArrayType])
        Some(tt)
      else
        None
    }

    def asArray: DataType = locked {
      filterArray.getOrElse {
        ArrayType(tt)
      }
    }

    def ensureArray: DataType = locked {
      filterArray.getOrElse {
        throw new UnsupportedOperationException(s"Type $tt is not an Array")
      }
    }

    def =~=(another: DataType): Boolean = {
      val result = (tt eq another) ||
        (tt == another) ||
        (tt.reified == another.reified)

      result
    }

    def should_=~=(another: DataType): Unit = {
      val result = =~=(another)
      assert(
        result,
        s"""
           |Type not equal:
           |LEFT:  $tt -> ${tt.reified}
           |RIGHT: $another -> ${another.reified}
          """.stripMargin
      )
    }
  }

  trait AsCatalystType[T] {

    def self: ScalaType[T]
  }
}
