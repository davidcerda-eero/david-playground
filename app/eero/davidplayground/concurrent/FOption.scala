//package eero.davidplayground.concurrent
//
//import eero.core.concurrent.TestAwaitable
//
//import scala.collection.compat._
//import scala.concurrent._
//import scala.concurrent.duration._
//
//object FOption {
//
//  def apply[T](t: T): FOption[T] = {
//    FOption(Future.successful(Option(t)))
//  }
//
//  def apply[T](o: Option[T]): FOption[T] = {
//    FOption(Future.successful(o))
//  }
//
//  def apply[T](f: Future[T])(implicit ec: ExecutionContext): FOption[T] = {
//    FOption(f.map(Option(_)))
//  }
//
//  def apply[T](f: Option[Future[T]])(implicit ec: ExecutionContext): FOption[T] = {
//    FOption(
//      f match {
//        case Some(a) => a.map(Some(_))
//        case None => Future.successful(None)
//      }
//    )
//  }
//
//  def cond[T](cond: Boolean, fFunc : => Future[T])(implicit ec: ExecutionContext): FOption[T] = {
//    FOption(if (cond) fFunc.map(Some(_)) else Future.successful(None))
//  }
//
//  def empty[A]: FOption[A] = FOption(Option.empty[A])
//
//  /*
//   * FutureO.sequence will result in Future.failed(Exception) if
//   * any FutureO in sequence evaluates as failed future. If all Futures
//   * succeed, then the returned FutureO value will always contain a non-None, `Some(M[X])` option
//   */
//  final def sequence[A, CC[X] <: IterableOnce[X], To](
//    in: CC[FOption[A]]
//  )(implicit bf: BuildFrom[CC[FOption[A]], A, CC[A]], executor: ExecutionContext): FOption[CC[A]] = {
//    val f = in.iterator
//      .foldLeft(Future.successful(bf.newBuilder(in))) { (fr, fa) =>
//        for {
//          r <- fr
//          ao <- fa.futureOpt
//        } yield ao.fold(r)(a => r += a)
//      }
//      .map(_.result())
//    FOption(f)
//  }
//
//}
//
//final case class FOption[+A](futureOpt: Future[Option[A]]) extends TestAwaitable[A] {
//
//  def flatten[B](implicit ev: A <:< FOption[B], ec: ExecutionContext): FOption[B] = flatMap(identity(_))(ec)
//
//  def flatMap[B](block: A => FOption[B])(implicit ec: ExecutionContext): FOption[B] = {
//    FOption {
//      futureOpt.flatMap {
//        case Some(a) => block(a).futureOpt
//        case None => Future.successful(None)
//      }
//    }
//  }
//
//  def map[B](block: A => B)(implicit ec: ExecutionContext): FOption[B] =
//    FOption(futureOpt.map(_.map(block)))
//
//  def getOrElse[B >: A](default: => B)(implicit ec: ExecutionContext): Future[B] =
//    futureOpt.map(_.getOrElse(default))
//
//  def getOrThrow(t: => Throwable)(implicit ec: ExecutionContext): Future[A] =
//    futureOpt.map(_.getOrElse(throw t))
//
//  def flatGetOrElse[B >: A](default: => Future[B])(implicit ec: ExecutionContext): Future[B] =
//    futureOpt.flatMap(_.fold(default)(self => Future.successful(self)))
//
//  def exists(p: A => Boolean)(implicit ec: ExecutionContext): Future[Boolean] = {
//    futureOpt.map(_.exists(p))
//  }
//
//  def filter(p: A => Boolean)(implicit ec: ExecutionContext): FOption[A] = {
//    FOption(futureOpt.map(_.filter(p)))
//  }
//
//  /** Used by for-comprehensions.
//    */
//  def withFilter(p: A => Boolean)(implicit ec: ExecutionContext): FOption[A] = filter(p)(ec)
//
//  def filterNot(p: A => Boolean)(implicit ec: ExecutionContext): FOption[A] = {
//    FOption(futureOpt.map(_.filterNot(p)))
//  }
//
//  def fold[B](ifEmpty: => B)(block: A => B)(implicit ec: ExecutionContext): Future[B] =
//    futureOpt.map(_.fold(ifEmpty)(block))
//
//  def forall(p: A => Boolean)(implicit ec: ExecutionContext): Future[Boolean] = {
//    futureOpt.map(_.forall(p))
//  }
//
//  def flatFold[B](ifEmpty: => Future[B])(block: A => Future[B])(implicit ec: ExecutionContext): Future[B] =
//    futureOpt.flatMap(_.fold(ifEmpty)(block))
//
//  def orElse[B >: A](alternative: => FOption[B])(implicit ec: ExecutionContext): FOption[B] = {
//    FOption {
//      futureOpt.flatMap { a =>
//        if (a.isEmpty) alternative.futureOpt else futureOpt
//      }
//    }
//  }
//
//  def foreach[B](block: A => B)(implicit ec: ExecutionContext): Unit =
//    futureOpt.foreach(_.foreach(block))
//
//  def isDefined(implicit ec: ExecutionContext): Future[Boolean] = {
//    futureOpt.map(_.isDefined)
//  }
//
//  def isEmpty(implicit ec: ExecutionContext): Future[Boolean] = {
//    futureOpt.map(_.isEmpty)
//  }
//
//  def nonEmpty(implicit ec: ExecutionContext): Future[Boolean] = isDefined
//
//  def toLeft[R](right: => R)(implicit ec: ExecutionContext): FEither[A, R] = {
//    FOutcome(futureOpt.map(_.toLeft(right)))
//  }
//
//  def toRight[L](left: => L)(implicit ec: ExecutionContext): FEither[L, A] = {
//    FOutcome(futureOpt.map(_.toRight(left)))
//  }
//
//  override private[core] def awaitable: Awaitable[A] = new Awaitable[A] {
//    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
//      futureOpt.ready(atMost)
//      this
//    }
//
//    def result(atMost: Duration)(implicit permit: CanAwait): A =
//      futureOpt.result(atMost).get
//  }
//
//  /**
//    * Creates a new FOption that will handle any matching throwable that this FOption might contain.
//    * If there is no match, or if this FOption contains a valid result then the new FOption will contain the same.
//    *
//    * Example:
//    *
//    * {{{
//    * FOption(6 / 0).recover { case e: ArithmeticException => None } // result: FOption.empty
//    * FOption(6 / 0).recover { case e: ClassNotFoundException => None } // result: Failed(ArithmeticException)
//    * FOption(6 / 2).recover { case e: ArithmeticException => None } // result: 3
//    * }}}
//    *
//    * @tparam B the type of the returned `FOption`
//    * @param pf the `PartialFunction` to apply if this `FOption` fails
//    * @return an `FOption` with the successful value of this `FOption` or the result of the `PartialFunction`
//    * @group Transformations
//    */
//  def recover[B >: A](pf: PartialFunction[Throwable, Option[B]])(implicit ec: ExecutionContext): FOption[B] = {
//    FOption(futureOpt.recover(pf))
//  }
//
//  /**
//    * Creates a new FOption that will handle any matching throwable that this FOption might contain
//    * by assigning it a result from another FOption.
//    *
//    * If there is no match, or if this FOption contains
//    * a valid result then the new FOption will contain the same result.
//    *
//    * Example:
//    *
//    * {{{
//    * val fo = FOption(Int.MaxValue)
//    * FOption(6 / 0).recoverWith { case e: ArithmeticException => fo } // result: Int.MaxValue
//    * }}}
//    *
//    * @tparam U the type of the returned `FOption`
//    * @param pf the `PartialFunction` to apply if this `FOption` fails
//    * @return an `FOption` with the successful value of this `FOption` or the outcome of the `FOption` returned by the `PartialFunction`
//    * @group Transformations
//    */
//  def recoverWith[B >: A](pf: PartialFunction[Throwable, FOption[B]])(implicit ec: ExecutionContext): FOption[B] = {
//    FOption(futureOpt.recoverWith(pf.andThen(_.futureOpt)))
//  }
//
//}
