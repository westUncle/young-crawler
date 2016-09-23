package com.young.crawler.cluster

import akka.actor.{ActorPath, ActorSystem, Props, RootActorPath}
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.protobuf.msg.ClusterMessages.MemberStatus
import com.typesafe.config.ConfigFactory

/**
 * Created by dell on 2016/9/19.
 */
class EventProcessor extends ClusterRoledWorker {
  override def receive: Receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      // 将processor注册到上游的collector中
      register(member, getProcessorPath)
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up).foreach(register(_, getProcessorPath))
    case UnreachableMember(member) =>
      log.info("Member detected as Unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent => // ignore
    case FilteredRecord(sourceHost, eventCode, line, nginxDate, realIp) => {
      val data = process(eventCode, line, nginxDate, realIp)
      log.info("Processed: data=" + data)
      // 将解析后的消息一JSON字符串的格式，保存到Kafka中
    }
  }

  def getProcessorPath(member: Member): ActorPath = {
    RootActorPath(member.address) / "user" / "interceptingActor"
  }

  private def process(eventCode: String, line: String, eventDate: String, realIp: String): Map[String,String] = {
    Map[String,String]()
  }
}

object EventProcessor{
  def main(args: Array[String]) {
    // 启动了5个EventProcessor
    Seq("2951","2952", "2953", "2954", "2955") foreach { port =>
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [processor]"))
        .withFallback(ConfigFactory.load())
      val system = ActorSystem("event-cluster-system", config)
      val processingActor = system.actorOf(Props[EventProcessor], name = "processingActor")
      system.log.info("Processing Actor: " + processingActor)
  }

  }

}