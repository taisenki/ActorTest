package org.taisenki.actortest.simple

import akka.actor._

/**
 * 实现简单的Actor架构功能展示1
 * Created by taisenki on 2016/8/23.
 */
object ActorSimple2 extends App {

  object Student{
    case object InitSignal
    case object QuoteRequest
    case class QuoteResponse(msg:String)
    case class Greet(peer: ActorRef)
  }

  class Student(teacherPath:String) extends Actor with ActorLogging{
    import Student._
    val teacherActor = context.actorSelection(teacherPath)
    def receive ={
      case InitSignal=> {
        teacherActor !QuoteRequest
      }

      case QuoteResponse(msg) => {
        log.info ("Received QuoteResponse from Teacher")
        log.info(s"Printing from Student Actor $msg")
      }
    }
  }

  class Teacher extends Actor with ActorLogging {
    import Student._
    val quotes = List("Moderation is for cowards",
      "Anything worth doing is worth overdoing",
      "The trouble is you think you have time",
      "You never gonna know if you never even try")
    def receive = {
      case QuoteRequest=>{
        import util.Random

        //Get a random Quote from the list and construct a response
        val quoteResponse = QuoteResponse(quotes(Random.nextInt(quotes.size)))

        //respond back to the Student who is the original sender of QuoteRequest
        sender ! quoteResponse
      }
    }
  }

  val system = ActorSystem("ActorTest")
  val teacher = system.actorOf(Props[Teacher],"teacher")
  val student = system.actorOf(Props(new Student("/user/teacher")),"student")

  student ! Student.InitSignal
  student ! Student.InitSignal
  student ! Student.InitSignal

  Thread sleep 2000

  system.shutdown()
}
