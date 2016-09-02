package org.taisenki.actortest.remote

import akka.actor.{ActorSelection, Props, Actor, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import RemoteActor._

import scala.concurrent.duration._

/**
 * Created by taisenki on 2016/8/23.
 */
object LocalActor extends App{

  class LocalActor extends Actor{
    //远程Actor
    var remote : ActorSelection = null
    override def preStart(): Unit = {
      remote = context.actorSelection("akka.tcp://RemoteActorTest@10.1.84.166:5150/user/RemoteActor")
      println("远程服务端地址 : " + remote)
    }

    override def receive: Receive = {
      case msg:String =>{
        println(s"LocalActor received message '$msg'")
      }
      case Result(a) =>{
        println(s"LocalActor receive result [$a]")
        sender ! "Thanks!"
      }
      case AddInt(a,b) => {
        println("Receivedtask from user:"+AddInt(a,b).toString())
        remote ! AddInt(a,b)
      }
      case End=>{
        println("Stop RemoteActor !")
        remote ! End
      }
      case _=>println("LocalActor got something unexpected.")
    }
  }

  val config=ConfigFactory.parseString(
    """  akka {
      |        loglevel = INFO #*必须设置成DEBUG 下面的debug才可以用
      |        #log-config-on-start = on #启动时显示用了哪个配置文件
      |        actor{
      |            provider= "akka.remote.RemoteActorRefProvider"
      |        }
      |        debug {
      |            receive = on #记录actor 接收的消息（user-level级）由akka.event.LoggingReceive处理
      |            autoreceive = on #记录所有自动接收的消息（Kill, PoisonPill）
      |            lifecycle = on #记录actor lifecycle changes
      |            fsm = on #状态机相关
      |            event-stream = on #记录eventSteam (subscribe/unsubscribe)
      |        }
      |        remote {
      |            log-sent-messages = on #记录出站的消息
      |            enabled-transports= ["akka.remote.netty.tcp"]
      |            transport= "akka.remote.netty.NettyRemoteTransport"
      |            netty.tcp{
      |             hostname= "10.1.84.166"
      |             port= 2562
      |            }
      |            log-received-messages = on #记录进站的消息
      |        }
      |        log-dead-letters = off #关闭对死信的日志打印记录
      |    }
    """.stripMargin)

  val system=ActorSystem("LocalActorTest", config.withFallback(ConfigFactory.load()))

  val local = system.actorOf(Props[LocalActor],"LocalActor")
  local ! "Hello, LocalActor !"
  local ! AddInt(2,3)
  local ! AddInt(5,6)

  Thread sleep 1000
  local ! End
  Thread sleep 1000
  system.shutdown()
}
