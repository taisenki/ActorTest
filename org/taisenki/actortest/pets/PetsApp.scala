package org.taisenki.actortest.pets

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference, AtomicInteger}
import java.util.function.IntUnaryOperator
import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.util.Random

/**
 * Created by taisenki on 2016/9/1.
 */
object PetsApp extends App{

  case object Buy
  case object LifeEcho
  case object LifeEnd
  case object `I am terribly hungry`
  case class `Give food`(num:Int)
  case object `Play with me`
  case object Play
  case class CatchFood(num:Int)
  case class NewPet(pet:ActorRef)
  case class TellPet(master:ActorRef)

  val baseLifetime = 100 seconds
  val variableLifetime = 30 seconds

  val baseEchotime = 2 seconds
  val variableEchotime = 1 seconds

  def scaledDuration(base: FiniteDuration, variable: FiniteDuration) =
    base + variable * Random.nextInt(1000) / 1000

  class Pet(name:String) extends Actor with ActorLogging{
    import context.dispatcher

    val `Degree of starvation` = new AtomicInteger(50)
    val master = new AtomicReference[ActorRef]()

    scheduler.scheduleOnce(scaledDuration(baseLifetime, variableLifetime),self,LifeEnd)
    scheduleLife

    def scheduleLife = {
      val nextTime = scaledDuration(baseEchotime, variableEchotime)
      scheduler.scheduleOnce(nextTime, self, LifeEcho)
    }

    override def receive: Receive = {
      case TellPet(master) => {
        this.master.set(master)
        scheduler.scheduleOnce((Random.nextInt(10)+3) seconds,master,`Play with me`)
      }
      case `Give food`(num) => {
        log.info(s"Give ${name} food !")
        `Degree of starvation`.addAndGet(num*10)
        `Degree of starvation`.getAndUpdate(lifes)
      }
      case Play => {
        log.info(s"Play with ${name} !")
        `Degree of starvation`.addAndGet(-10)
        Thread sleep 1000
        scheduler.scheduleOnce((Random.nextInt(10)+3) seconds,sender,`Play with me`)
      }
      case LifeEnd => {
        context.system.stop(self)
        Thread sleep 1000
      }
      case LifeEcho =>{
        life
        scheduleLife
      }
    }

    object lifes extends IntUnaryOperator{
      override def applyAsInt(operand: Int): Int = {
        if(operand>100) 100 else operand
      }
    }

    def life: Unit ={
      val now = `Degree of starvation`.addAndGet(-3)
      if (now<40) master.get() ! `I am terribly hungry`
      log.info(s"$name has life [$now/100]")
      if (now<0) self ! LifeEnd
    }
  }
  class Master(shop: ActorRef) extends Actor with ActorLogging{
    import context.dispatcher

    implicit val timeout: Timeout = 5 seconds
    val foods = new AtomicInteger(0)
    scheduler.scheduleOnce(200 second)(context.system.terminate())

    override def preStart: Unit ={
      super.preStart
      shop ! Buy
    }
    override def receive: Receive = {
      case `I am terribly hungry` => {
        val ss = Random.nextInt(4)+2
        if (foods.get() > ss) {
          foods.getAndAdd(-ss)
          sender ! `Give food`(ss)
        } else {
          val future = shop ? Buy
          val result = Await.result(future, timeout.duration).asInstanceOf[CatchFood]
          foods.addAndGet(result.num - ss)
          sender ! `Give food`(ss)
        }
      }
      case `Play with me` =>{
        sender ! Play
      }
      case Terminated(ref) => {
        log.info(s"Oh ! My pet ${ref.path.name} has dead !")
        Thread sleep 1000
        shop ! Buy
      }
      case CatchFood(num) => {
        log.info(s"I have get ${num} foods !")
        foods.getAndAdd(num)
      }
      case NewPet(pet) => {
        log.info(s"I have a new pet ${pet.path.name} !")
        context.watch(pet)
        pet ! TellPet(self)
      }
    }
  }

  /**
   * PetShop初次售出宠物后，只进行食物售出直到宠物死亡
   */
  class PetShop extends Actor with ActorLogging{

    val petsName = List("alice","bob","kali","lily","white")

    override def receive: Receive = {
      case Buy =>{
        val name = petsName(Random.nextInt(5))
        val newpet = context.system.actorOf(Props(classOf[Pet],name),name)
        log.info(s"Pet ${name} has born !")
        sender ! NewPet(newpet)
        context.watch(newpet)
        context become foodShop
      }
    }

    def foodShop: Receive ={
      case Buy => sender!CatchFood(Random.nextInt(10)+5)
      case Terminated(ref) => context become receive
    }
  }

  val config=ConfigFactory.parseString(
    """  akka {
      |        loglevel = INFO #*必须设置成DEBUG 下面的debug才可以用
      |        #log-config-on-start = on #启动时显示用了哪个配置文件
      |        debug {
      |            receive = on #记录actor 接收的消息（user-level级）由akka.event.LoggingReceive处理
      |            autoreceive = on #记录所有自动接收的消息（Kill, PoisonPill）
      |            lifecycle = on #记录actor lifecycle changes
      |            fsm = on #状态机相关
      |            event-stream = on #记录eventSteam (subscribe/unsubscribe)
      |        }
      |        remote {
      |            log-sent-messages = on #记录出站的消息
      |            log-received-messages = on #记录进站的消息
      |        }
      |        #log-dead-letters = off #关闭对死信的日志打印记录
      |    }
    """.stripMargin)

  val system=ActorSystem("ActorTest", config.withFallback(ConfigFactory.load()))

  val scheduler = system.scheduler

  val shopActor = system.actorOf(Props[PetShop],"shopActor")
  val masterActor = system.actorOf(Props(new Master(shopActor)),"master")

  println(shopActor.path.name)
}
