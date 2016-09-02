package org.taisenki.actortest.pets

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.function.IntUnaryOperator

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
 * Created by taisenki on 2016/9/1.
 */
object PetsApp2 extends App{

  val baseLifetime = 200 seconds
  val variableLifetime = 20 seconds

  val baseWorktime = 10 seconds
  val variableWorktime = 2 seconds

  def scaledDuration(base: FiniteDuration, variable: FiniteDuration) =
    base + variable * Random.nextInt(1000) / 1000

  //与shop交互
  object PetShop{
    case object GetPet
    sealed class Goods(val num:Int,val price:Int)
    case class Buy(money:Int,goods: Goods)
    case class Food(override val num:Int) extends Goods(num,3)
    case class Tool(override val num:Int) extends Goods(num,5)
    case class `Give change`(money:Int)
    case object `Money not enough`
  }

  /**
   * PetShop初次售出宠物后，只进行食物售出直到宠物死亡
   */
  class PetShop extends Actor with ActorLogging{
    import PetShop._

    val petsName = List("Bob", "Alice", "Rock", "Paper", "Scissors", "North", "South", "East",
      "West", "Up", "Down")
    var nextNameIndex = 0
    val nameIndexLimit = petsName.length * (petsName.length + 1)

    override def receive: Receive = {
      case GetPet =>{
        val name =
          if (nextNameIndex < petsName.length) petsName(nextNameIndex)
          else {
            val first = nextNameIndex / petsName.length - 1
            val second = nextNameIndex % petsName.length
            petsName(first) + "-" + petsName(second)
          }
        sender ! name
        nextNameIndex = (nextNameIndex + 1) % nameIndexLimit
      }
      case Buy(money,goods:Goods)=>{
        val minus = money - goods.num*goods.price
        if(minus>=0) {
          sender ! `Give change`(minus)
          sender ! goods
        }else{
          sender ! `Money not enough`
        }
      }
    }
  }

  object Company{
    case class Apply(name:String)
    case object Employing
    case object Reject

    case object StartWork
    case object EndWork

    case class Salary(money:Int)

    val baseSalarytime = 50 seconds
    val variableSalarytime = 10 seconds
  }
  class Company extends Actor with ActorLogging{
    import Company._
    import context.dispatcher
    val liveEmployers = mutable.Buffer[(ActorRef, String)]()

    def charge(salary: Salary) ={
      liveEmployers.foreach(_._1 ! salary)
      val ss = scaledDuration(baseSalarytime,variableSalarytime)
      scheduler.scheduleOnce(ss,self,Salary(ss.toSeconds.toInt/3))
    }
    charge(Salary(0))

    override def receive: Receive = {
      case Apply(name) =>{
        if(liveEmployers.size<5){
          liveEmployers += ((sender,name))
          context.watch(sender)
          sender ! Employing
        } else {
          sender ! Reject
        }
      }
      case Terminated(ref) => {
        val woker = (liveEmployers.find(_._1 == ref)).get
        liveEmployers -= woker
        println(s"${woker._2} has left the business\nCompany now tracking ${liveEmployers.size} Employers")
        if(liveEmployers.size == 0) {
          log.info("There are no employers, the Company is down !")
          context.system.terminate()
        }
      }
      case StartWork =>{
        liveEmployers.foreach(_._1 ! StartWork)
        scheduler.scheduleOnce(scaledDuration(baseWorktime,variableWorktime),self,EndWork)
      }
      case EndWork =>{
        liveEmployers.foreach(_._1 ! EndWork)
        scheduler.scheduleOnce(scaledDuration(baseWorktime,variableWorktime),self,StartWork)
      }
      case salary:Salary => charge(salary)
    }
  }


  object Human{
    case class `Give food`(num:Int)
    case class `Give tool`(num:Int)
    case object Play
    case object Working
  }

  class Human(name:String) extends Actor with ActorLogging{
    import Company._
    import Human._
    import Pet._
    import PetShop._

    implicit val timeout: Timeout = 5 seconds
    val foods = new AtomicInteger(0)
    val tools = new AtomicInteger(0)
    val moneys = new AtomicInteger(0)

    var needFood = false
    var needTool = false

    override def preStart(): Unit ={
      super.preStart()
      companyActor ! Apply(name)
    }

    var pets:ActorRef = null

    override def receive: Receive = {
      case Employing=>{
        log.info("I have get a job !")
        context become recreation
      }
    }

    def recreation:Receive ={
      case msg:String =>{
        pets = context.actorOf(Props(classOf[Pet],msg),msg)
        log.info(s"I have a new pet ${pets.path.name} !")
        context.watch(pets)
      }
      case `I am terribly hungry` => {
        val ss = Random.nextInt(4)+2
        if (foods.get() > ss) {
          foods.getAndAdd(-ss)
          pets ! `Give food`(ss)
          needFood = false
        } else {
          needFood = true
          shopActor ! Buy(moneys.get(),Food(10))
        }
      }
      case `Play with me` =>{
        sender ! Play
      }
      case `I am not happy` =>{
        val ss = Random.nextInt(4)+2
        if (tools.get() > ss) {
          tools.getAndAdd(-ss)
          pets ! `Give tool`(ss)
          needTool = false
        } else {
          needTool = true
          shopActor ! Buy(moneys.get(),Tool(10))
        }
      }
      case Terminated(ref) => {
        log.info(s"Oh ! My pet ${ref.path.name} has dead ! I not want to work !")
        Thread sleep 1000
        context stop self
      }
      case Food(num) => {
        log.info(s"I have get ${num} foods !")
        foods.getAndAdd(num)
        if(needFood){
          self ! `I am terribly hungry`
        }
      }
      case Tool(num) => {
        log.info(s"I have get ${num} tools !")
        tools.getAndAdd(num)
        if(needTool){
          self ! `I am not happy`
        }
      }
      case Salary(money) =>{
        log.info(s"I have get wages ${money} !")
        moneys.addAndGet(money)
        if(pets == null) {
          log.info("I want get a pet!")
          shopActor ! GetPet
        }
      }
      case StartWork =>{
        log.info("I am go to work !")
        pets ! Working
        context become worker
      }
      case `Give change`(num) => {
        log.info(s"Now I have money $num !")
        moneys.getAndSet(num)
      }
      case `Money not enough` => {
        log.info(`Money not enough`.toString)
      }
    }

    def worker:Receive={
      case EndWork =>{
        log.info("Complete the work !")
        context become receive
      }
      case Salary(money) =>{
        log.info(s"I have get wages ${money} !")
        moneys.addAndGet(money)
//        if(pets == null) {
//          log.info("I want get a pet!")
//          shopActor ! GetPet
//        }
      }
      case Terminated(ref) => {
        log.info(s"Oh ! My pet ${ref.path.name} has dead ! I not want to work !")
        Thread sleep 1000
        context stop self
      }
      case _ =>{
        sender ! Working
      }
    }
  }

  object Pet{
    case object `I am terribly hungry`
    case object `Play with me`
    case object `I am not happy`
    case object LifeEcho
    case object LifeEnd

    val baseEchotime = 2 seconds
    val variableEchotime = 1 seconds
  }

  class Pet(name:String) extends Actor with ActorLogging{
    import Human._
    import Pet._
    import context.dispatcher

    val `Degree of starvation` = new AtomicInteger(50)
    val `Degree of happy` = new AtomicInteger(50)

    scheduler.scheduleOnce((Random.nextInt(10)+3) seconds,context.parent,`Play with me`)
    scheduler.scheduleOnce(scaledDuration(baseLifetime, variableLifetime),self,LifeEnd)
    scheduleLife

    def scheduleLife = {
      val nextTime = scaledDuration(baseEchotime, variableEchotime)
      scheduler.scheduleOnce(nextTime, self, LifeEcho)
    }

    override def receive: Receive = {
      case `Give food`(num) => {
        log.info(s"Give ${name} food !")
        `Degree of starvation`.addAndGet(num*10)
        `Degree of starvation`.getAndUpdate(lifes)
      }
      case `Give tool`(num) =>{
        log.info(s"Give ${name} tool !")
        `Degree of happy`.addAndGet(num*10)
        `Degree of happy`.getAndUpdate(lifes)
      }
      case Play => {
        log.info(s"Play with ${name} !")
        `Degree of starvation`.addAndGet(-5)
        Thread sleep 2000
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
      if (now<0) {
        context.system.stop(self)
        return
      }
      if (now<25) context.parent ! `I am terribly hungry`
      val nowa = `Degree of happy`.addAndGet(-3)
      if (nowa<0) {
        context.system.stop(self)
        return
      }
      if (nowa<25) context.parent ! `I am not happy`
      log.info(s"$name has life [$now/100], happy [$nowa/100]")
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

  val companyActor = system.actorOf(Props[Company],"company")
  val shopActor = system.actorOf(Props[PetShop],"shopActor")
  val humanActor = system.actorOf(Props(classOf[Human],"HanYang"),"HanYang")
  Thread sleep 2000
  val humanActor2 = system.actorOf(Props(classOf[Human],"ZhangShuai"),"ZhangShuai")
  Thread sleep 2000
  val humanActor3 = system.actorOf(Props(classOf[Human],"LiShan"),"LiShan")
}
