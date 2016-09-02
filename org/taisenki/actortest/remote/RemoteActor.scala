package org.taisenki.actortest.remote

import akka.actor.{Props, Actor, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
/**
 * Created by taisenki on 2016/8/23.
 */
object RemoteActor extends App{

  case class AddInt(a:Int,b:Int)
  case class Result(result:Int)
  case object End

  class RemoteActor extends Actor{
    override def receive: Receive = {
      case msg:String =>{
        println(s"RemoteActor received message '$msg'")
      }
      case AddInt(a,b) => sender ! Result(a+b)
      case End =>{
        println("RemoteActor received ending message !")
        sender ! "RemoteActor ending !"
        Thread sleep 1000
        context.system.shutdown()
      }
      case _=>println("RemoteActor got something unexpected.")
    }
  }

  val config=ConfigFactory.parseString(
    """  akka {
      |        actor{
      |            provider= "akka.remote.RemoteActorRefProvider"
      |        }
      |        remote {
      |            log-sent-messages = on #记录出站的消息
      |            enabled-transports= ["akka.remote.netty.tcp"]
      |            netty.tcp{
      |               hostname= "10.1.84.166"
      |               port= 5150
      |            }
      |            log-received-messages = on #记录进站的消息
      |        }
      |    }
    """.stripMargin)

  val system=ActorSystem("RemoteActorTest", config.withFallback(ConfigFactory.load()))
  val remote = system.actorOf(Props[RemoteActor],"RemoteActor")
  remote ! "Hello, RemoteActor !"
  println(remote.path)
}
