package org.taisenki.actortest.simple

import akka.actor._

/**
 * 展示Actor内部调用相关hook的执行过程
 * Created by taisenki on 2016/8/31.
 */
object ActorSimple4 extends App {

  case object Down
  class Simple4 extends Actor with ActorLogging{
    override def preStart(): Unit ={
      log.info("Actor will Start !")
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      super.preRestart(reason,message)
      log.info("Actor will ReStart !")
    }

    override def postRestart(reason: Throwable): Unit = {
      super.postRestart(reason)
      log.info("Actor has ReStart !")
    }

    override def postStop(): Unit ={
      log.info("Actor is End !")
    }

    def receive={
      case msg:String => log.info(s"Actor receive $msg")
      case Down => throw new Exception("Down !")
    }
  }


  val system = ActorSystem("ActorTest")
  val actor = system.actorOf(Props[Simple4],"simple4")
  actor ! "hello"
  actor ! Down
  Thread sleep 1000
  actor ! "hello"
  actor ! "hello"
  Thread sleep 1000

  system.terminate()
}
