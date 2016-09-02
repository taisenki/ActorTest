package org.taisenki.actortest.test

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.taisenki.actortest.simple
import org.taisenki.actortest.simple.ActorSimple2.Student.{QuoteRequest, InitSignal}
import org.taisenki.actortest.simple.ActorSimple2.Teacher

/**
 * Created by taisenki on 2016/8/23.
 */
class ActorTest extends TestKit(ActorSystem("ActorTest",ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]""")))
with WordSpecLike
with MustMatchers
with BeforeAndAfterAll{

  "A student" must {

    "log a QuoteResponse eventually when an InitSignal is sent to it" in {

      import simple.ActorSimple2.{Student, Teacher}

      val teacherRef = TestActorRef(Props[Teacher], "teacherActor")
      val studentRef = TestActorRef(new Student(teacherRef.path.toString), "studentActor")

      studentRef.underlyingActor.teacherActor.pathString must be ("/user/teacherActor")
      EventFilter.info (start="Printing from Student Actor", occurrences=1).intercept{
        studentRef ! InitSignal
      }
    }
  }

  //1. Sends message to the Print Actor. Not even a testcase actually
  "A teacher" must {

    "print a quote when a QuoteRequest message is sent" in {

      val teacherRef = TestActorRef[Teacher]
      teacherRef ! QuoteRequest
    }
  }


  //2. Sends message to the Log Actor. Again, not a testcase per se
  "A teacher with ActorLogging" must {

    "log a quote when a QuoteRequest message is sent" in {

      val teacherRef = TestActorRef[Teacher]
      teacherRef ! QuoteRequest
    }
  }



  override def afterAll(): Unit ={
    super.afterAll()
    system.terminate()
  }

}
