/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

package scala.actors

import scala.collection.mutable.Queue

/**
 * The Reactor trait provides lightweight actors.
 *
 * @author Philipp Haller
 */
trait Reactor extends OutputChannel[Any] {

  /* The actor's mailbox. */
  protected val mailbox = new MessageQueue

  protected var sendBuffer = new Queue[(Any, OutputChannel[Any])]

  /* If the actor waits in a react, continuation holds the
   * message handler that react was called with.
   */
  protected var continuation: PartialFunction[Any, Unit] = null

  /* Whenever this Actor executes on some thread, waitingFor is
   * guaranteed to be equal to waitingForNone.
   *
   * In other words, whenever waitingFor is not equal to
   * waitingForNone, this Actor is guaranteed not to execute on some
   * thread.
   */
  protected val waitingForNone = (m: Any) => false
  protected var waitingFor: Any => Boolean = waitingForNone

  /**
   * The behavior of an actor is specified by implementing this
   * abstract method.
   */
  def act(): Unit

  protected[actors] def exceptionHandler: PartialFunction[Exception, Unit] =
    Map()

  protected[actors] def scheduler: IScheduler =
    Scheduler

  protected[actors] def mailboxSize: Int =
    mailbox.size

  /**
   * Sends <code>msg</code> to this actor (asynchronous) supplying
   * explicit reply destination.
   *
   * @param  msg      the message to send
   * @param  replyTo  the reply destination
   */
  def send(msg: Any, replyTo: OutputChannel[Any]) {
    val todo = synchronized {
      if (waitingFor ne waitingForNone) {
        val savedWaitingFor = waitingFor
        waitingFor = waitingForNone
        () => scheduler execute (makeReaction(() => {
          val startMbox = new MessageQueue
          synchronized { startMbox.append(msg, replyTo) }
          searchMailbox(startMbox, savedWaitingFor, true)
        }))
      } else {
        sendBuffer.enqueue((msg, replyTo))
        () => { /* do nothing */ }
      }
    }
    todo()
  }

  protected[this] def makeReaction(fun: () => Unit): Runnable =
    new ReactorTask(this, fun)

  protected[this] def resumeReceiver(item: (Any, OutputChannel[Any]), onSameThread: Boolean) {
    // assert continuation != null
    if (onSameThread)
      continuation(item._1)
    else
      scheduleActor(null, item._1)
  }

  def !(msg: Any) {
    send(msg, null)
  }

  def forward(msg: Any) {
    send(msg, null)
  }

  def receiver: Actor = this.asInstanceOf[Actor]

  protected[this] def drainSendBuffer(mbox: MessageQueue) {
    while (!sendBuffer.isEmpty) {
      val item = sendBuffer.dequeue()
      mbox.append(item._1, item._2)
    }
  }

  // assume continuation has been set
  protected[this] def searchMailbox(startMbox: MessageQueue,
                                    handlesMessage: Any => Boolean,
                                    resumeOnSameThread: Boolean) {
    var tmpMbox = startMbox
    var done = false
    while (!done) {
      val qel = tmpMbox.extractFirst((m: Any) => handlesMessage(m))
      if (tmpMbox ne mailbox)
        tmpMbox.foreach((m, s) => mailbox.append(m, s))
      if (null eq qel) {
        synchronized {
          // in mean time new stuff might have arrived
          if (!sendBuffer.isEmpty) {
            tmpMbox = new MessageQueue
            drainSendBuffer(tmpMbox)
            // keep going
          } else {
            waitingFor = handlesMessage
            done = true
          }
        }
      } else {
        resumeReceiver((qel.msg, qel.session), resumeOnSameThread)
        done = true
      }
    }
  }

  protected[actors] def react(f: PartialFunction[Any, Unit]): Nothing = {
    assert(Actor.rawSelf(scheduler) == this, "react on channel belonging to other actor")
    synchronized { drainSendBuffer(mailbox) }
    continuation = f
    searchMailbox(mailbox, f.isDefinedAt, false)
    throw Actor.suspendException
  }

  protected def scheduleActor(f: PartialFunction[Any, Unit], msg: Any) = {
    scheduler execute (new LightReaction(this,
                                         if (f eq null) continuation else f,
                                         msg))
  }

  def start(): Reactor = {
    scheduler execute {
      scheduler.newActor(this)
      (new LightReaction(this)).run()
    }
    this
  }

  /* This closure is used to implement control-flow operations
   * built on top of `seq`. Note that the only invocation of
   * `kill` is supposed to be inside `Reaction.run`.
   */
  private[actors] var kill: () => Unit =
    () => { exit() }

  private[actors] def seq[a, b](first: => a, next: => b): Unit = {
    val s = Actor.rawSelf(scheduler)
    val killNext = s.kill
    s.kill = () => {
      s.kill = killNext

      // to avoid stack overflow:
      // instead of directly executing `next`,
      // schedule as continuation
      scheduleActor({ case _ => next }, 1)
      throw Actor.suspendException
    }
    first
    throw new KillActorException
  }

  protected[actors] def exit(): Nothing = {
    terminated()
    throw Actor.suspendException
  }

  protected[actors] def terminated() {
    scheduler.terminated(this)
  }

}