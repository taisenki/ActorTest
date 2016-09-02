package org.taisenki.actortest.simple

import akka.actor.{Props, Actor, ActorSystem}

/**
 * 实现简单的Actor通信及交互功能
 * Created by taisenki on 2016/8/23.
 */
object ActorSimple1 extends App{
  case object Start
  case object End
  case class Error(msg:String)

  class Simple1 extends Actor{
    val actor = context.actorOf(Props[Simple2],"simple2")
    override def receive: Receive = {
      case Start => actor ! Start
      case End => {
        println(sender().path.name)
        actor ! End
        context.system.shutdown()
      }
      case Error(msg) => println(s"Program error with $msg")
    }
  }

  val system = ActorSystem("ActorTest")
  val actor = system.actorOf(Props[Simple1],"simple1")
  actor ! Start
  actor ! Error (" Manual Error")
  actor ! End
  Thread sleep 1000

  class Simple2 extends Actor{
    def receive: Receive = {
      case Start => println("simple2 is start !")
      case End => {
        sender ! Error("simple2 is end !")
        context.system.stop(self)
      }
    }
  }
}
