/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */


package scala.collection
package parallel.immutable


import scala.collection.generic._

import scala.collection.parallel.ParIterableLike
import scala.collection.parallel.Combiner
import scala.collection.GenIterable


/** A template trait for immutable parallel iterable collections.
 *  
 *  $paralleliterableinfo
 *  
 *  $sideeffects
 *  
 *  @tparam T    the element type of the collection
 *  
 *  @author Aleksandar Prokopec
 *  @since 2.9
 */
trait ParIterable[+T]
extends collection/*.immutable*/.GenIterable[T]
   with collection.parallel.ParIterable[T]
   with GenericParTemplate[T, ParIterable]
   with ParIterableLike[T, ParIterable[T], collection.immutable.Iterable[T]]
{
  override def companion: GenericCompanion[ParIterable] with GenericParCompanion[ParIterable] = ParIterable
  
  // if `immutable.ParIterableLike` is introduced, please move these 4 methods there
  override def toIterable: ParIterable[T] = this
  
  override def toSeq: ParSeq[T] = toParCollection[T, ParSeq[T]](() => ParSeq.newCombiner[T])
  
}


/** $factoryInfo
 */
object ParIterable extends ParFactory[ParIterable] {
  implicit def canBuildFrom[T]: CanCombineFrom[Coll, T, ParIterable[T]] =
    new GenericCanCombineFrom[T]
  
  def newBuilder[T]: Combiner[T, ParIterable[T]] = ParVector.newBuilder[T]
  
  def newCombiner[T]: Combiner[T, ParIterable[T]] = ParVector.newCombiner[T]
  
}














