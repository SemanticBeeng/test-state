package teststate.run

import acyclic.file
import teststate.data._
import teststate.typeclass._
import teststate.core._
import teststate.vector1
import CoreExports._

object Runner {

  trait HalfCheck[O, S, Err] {
    type A
    val check: Around.DeltaAux[OS[O, S], Err, A]
    val before: Tri[Err, A]
  }

  def HalfCheck[O, S, Err, a](_check: Around.DeltaAux[OS[O, S], Err, a])(_before: Tri[Err, a]): HalfCheck[O, S, Err] =
    new HalfCheck[O, S, Err] {
      override type A = a
      override val check = _check
      override val before = _before
    }

  val ActionName    : Name = "Action"
  val PreName       : Name = "Pre-conditions"
  val PostName      : Name = "Post-conditions"
  val InvariantsName: Name = "Invariants"

  val Observation  = "Observation"
  val UpdateState  = "Update expected state"
  val InitialState = "Initial state."

  private case class Progress[F[_], R, O, S, E](queue  : Vector[Action[F, R, O, S, E]],
                                                ros    : ROS[R, O, S],
                                                history: History[E]) {
    def failure: Option[E] = history.failure
    def failed : Boolean   = history.failed

    def :+(s: History.Step[E])  = copy(history = history :+ s)
    def ++(s: History.Steps[E]) = copy(history = history ++ s)
    def ++(s: History[E])       = copy(history = history ++ s.steps)
  }

  case class ChecksAround[F[_], O, S, E](before: F[Point       [OS[O, S], E]],
                                         delta : F[Around.Delta[OS[O, S], E]],
                                         after : F[Point       [OS[O, S], E]])

  def prepareChecksAround[O, S, E](// invariants: Invariants[O, S, E],
                                   arounds   : Arounds[O, S, E],
                                   input     : Right[OS[O, S]]): ChecksAround[List, O, S, E] = {
    val bb = List.newBuilder[Point[OS[O, S], E]]
    val ba = List.newBuilder[Point[OS[O, S], E]]
    val bd = List.newBuilder[Around.Delta[OS[O, S], E]]

    val addAround: Around[OS[O, S], E] => Unit = {
      case a: Around.Delta[OS[O, S], E] => bd += a; ()
      case Around.Point(p, w) => w match {
        case Around.Before         => bb += p; ()
        case Around.After          => ba += p; ()
        case Around.BeforeAndAfter => bb += p; ba += p; ()
      }
    }

//    invariants.foreach(input) {
//      case Invariant.Point (p) => ()
//      case Invariant.Around(a) => addAround(a)
//    }

    arounds.foreach(input)(addAround)

    ChecksAround(bb.result(), bd.result(), ba.result())
  }

  def run[F[_], R, O, S, E](test: Test[F, R, O, S, E])
                           (initialState: S, ref: => R): F[History[E]] = {

    import test.content.{executionModel => EM, recover, invariants}

    val refFn = () => ref

    type H    = History[E]
    type ROS  = teststate.data.ROS[R, O, S]
    type Test = teststate.run.Test[F, R, O, S, E]
    type P    = Progress[F, R, O, S, E]

    def observe(test: Test): E Or O =
      recover.recover(test.observe.apply(ref), Left(_))

    def subtest(test: Test, initROS: ROS, summariseFinalResult: Boolean): F[P] = {

      // TODO Remove duplicate checks by reference
      //    val invariants = test.content.invariants
      //    val invariantsAround = test.content.invariants.getAround
      //    val invariantsPoints = test.content.invariants.getPoint.getSingles

      def checkAround[N, A](nameFn: NameFn[ROS], arounds: Arounds[O, S, E], collapse: Boolean, p: P)
                           (prepare: ROS => Option[A])
                           (run: A => F[(Name => H, ROS)]): F[P] = {

        val name = recover.name(nameFn, p.ros.some)

        prepare(p.ros) match {
//          case Some(a) =>
//
//            val checks = prepareChecksAround(arounds, p.ros.rOS)
//
//            // Perform before
//            val pre = {
//              val b = History.newBuilder[E]
//              b.addEach(checks.before)(_.check.name)(p.ros.sos, _.check.test(p.ros.os))
//              b.group(PreName)
//            }
//
//            if (pre.failed) {
//              EM.pure(p :+ History.parent(name, pre))
//
//            } else {
//
//              // Perform around-pre
//              val hcs = {
//                val b = Vector.newBuilder[HalfCheck[O, S, E]]
//                for (c0 <- checks.delta) {
//                  val c = c0.aux
//                  val r = recover.attempt(c.before(p.ros.os)).fold(Failed(_), identity)
//                  b += HalfCheck(c)(r)
//                }
//                b.result()
//              }
//
//              // Perform action
//              val runF = run(a)
//              EM.map(runF) { case (mkStep, ros2) =>
//
//                def addStep(s: History.Step[E]) =
//                  p.copy(ros = ros2, history = p.history :+ s)
//
//                val step = mkStep(ActionName)
//                val collapseIfNoPost = collapse && pre.isEmpty && step.steps.length == 1
//                def collapsed = step.steps(0).copy(name = name)
//                if (step.failed) {
//
//                  if (collapseIfNoPost)
//                    addStep(collapsed)
//                  else
//                    addStep(History.parent(name, pre ++ step))
//
//                } else {
//
//                  // Post conditions
//                  val post1 = {
//                    val b = History.newBuilder[E]
//                    //                  b.addEach(hcs)(_.check name omg.ros.sos, c => c.check.test(ros2.os, c.before_!)) // Perform around-post
//                    b.addEach(hcs)(
//                      c => c.check.name)(ros2.sos,
//                      c => c.before.flatMap(a => Tri failedOption c.check.test(ros2.os, a))) // Perform around-post
//                    b.addEach(checks.after)(_.check.name)(ros2.sos, _.check.test(ros2.os)) // Perform post
//                    b.group(PostName)
//                  }
//
//                  // Check invariants
//                  val invs = {
//                    val b = History.newBuilder[E]
//                    b.addEach(invariantsPoints)(_.name)(ros2.sos, _.test(ros2.os))
//                    b.group(InvariantsName)
//                  }
//
//                  val post = post1 ++ invs
//
//                  if (collapseIfNoPost && post.isEmpty)
//                    addStep(collapsed)
//                  else
//                    addStep(History.parent(name, pre ++ step ++ post))
//                }
//              }
//            }

          case None =>
            EM.pure(p :+ History.Step(name, Skip))
        }
      }

      def start(a: Action[F, R, O, S, E], ros: ROS, history: H = History.empty) =
        go(Progress(vector1(a), ros, history))

      def go(p0: P): F[P] =
        EM.tailrec(p0)(x => x.queue.isEmpty || x.failed) { p =>

          def continue(r: F[P]): F[P] =
            EM.map(r)(_.copy(queue = p.queue.tail))

          import p.ros
          p.queue.head match {

            // ==============================================================================
//            case Action.Single(nameFn, run, check) =>
//              val omg2F =
//                checkAround(nameFn, check & invariantsAround, true, p)(run) { act =>
//
//                  def ret(ros: ROS, r: Result[E], hs: History.Steps[E] = Vector.empty) =
//                    ((n: Name) => History(History.Step(n, r) +: hs), ros)
//
//                  EM.map(EM.recover(act())) {
//                    case Right(nextStateFn) =>
//                      observe(test) match {
//                        case Right(obs2) =>
//                          recover.attempt(nextStateFn(obs2)) match {
//                            case Right(Right(state2)) =>
//                              val ros2 = new ROS(refFn, obs2, state2)
//                              ret(ros2, Pass)
//                            case Right(Left(e)) =>
//                              ret(ros, Pass, vector1(History.Step(Observation, Fail(e))))
//                            case Left(e) =>
//                              ret(ros, Pass, vector1(History.Step(UpdateState, Fail(e))))
//                          }
//                        case Left(e) =>
//                          ret(ros, Pass, vector1(History.Step(Observation, Fail(e))))
//                      }
//                    case Left(e) =>
//                      ret(ros, Fail(e))
//                  }
//
//                }
//              continue(omg2F)
//
//            // ==============================================================================
//            case Action.Group(nameFn, actionFn, check) =>
//              val omg2F =
//                checkAround(nameFn, check & invariantsAround, false, p)(actionFn)(children =>
//                  EM.map(start(children, ros))(omgC =>
//                    ((_: Name) => omgC.history, omgC.ros))
//                )
//              continue(omg2F)
//
            // ==============================================================================
            case Action.SubTest(name, action, subInvariants) =>
              val t = new Test(new TestContent(action, invariants & subInvariants), test.observe)
              val subomg = subtest(t, ros, false)
              EM.map(subomg)(s =>
                Progress(
                  p.queue.tail,
                  s.ros,
                  p.history ++ History.maybeParent(name(ros.some), s.history)
                ))

            // ==============================================================================
            case Action.Composite(actions) =>
              EM.pure(p.copy(queue = p.queue.tail ++ actions.toVector))
          }
        }

      val finalResult: F[P] = {
        val ros = initROS

        val invariantsPoints = {
          val b = Vector.newBuilder[Point[OS[O, S], E]]
          invariants.foreach(ros.rOS) {
            case Invariant.Point(p)  => b += p; ()
            case Invariant.Around(_) => ()
          }
          b.result()
        }

        val firstSteps: H =
          if (invariantsPoints.isEmpty)
            History.empty
          else {
            val children = {
              val b = History.newBuilder[E]
              b.addEach(invariantsPoints)(_.name)(ros.sos, _ test ros.os)
              b.history()
            }
            History(History.parent(InitialState, children))
          }

        val fh: F[P] =
          if (firstSteps.failed)
            EM.pure(Progress[F, R, O, S, E](Vector.empty, ros, firstSteps))
          else
            start(test.content.action, ros, firstSteps)

        EM.map(fh) { omg =>
          import omg.{history => h}
          val h2: H =
            if (h.isEmpty)
              History(History.Step("Nothing to do.", Skip))
            else if (summariseFinalResult)
              h.result match {
                case Pass    => h :+ History.Step("All pass.", Pass)
                case Skip    => h :+ History.Step("All skipped.", Skip)
                case Fail(_) => h
              }
            else
              h
          omg.copy(history = h2)
        }
      }

      finalResult
    }

    observe(test) match {
      case Right(obs) =>
        val ros = new ROS(refFn, obs, initialState)
        EM.map(subtest(test, ros, true))(_.history)

      case Left(e) =>
        val s = History.Step(Observation, Fail(e))
        val h = History(History.parent(InitialState, History(s)))
        EM pure h
    }
  }
}

