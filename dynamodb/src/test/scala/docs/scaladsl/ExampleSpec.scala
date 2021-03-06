/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.scaladsl

import java.lang

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.alpakka.dynamodb.scaladsl._
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.Future

class ExampleSpec
    extends TestKit(ActorSystem("ExampleSpec"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  implicit val materializer: Materializer = ActorMaterializer()
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.millis)

  override def beforeAll() = {
    System.setProperty("aws.accessKeyId", "someKeyId")
    System.setProperty("aws.secretKey", "someSecretKey")
  }

  override def afterAll(): Unit = shutdown()

  "DynamoDB with external client" should {

    "provide a simple usage example" in {

      //##simple-request
      import DynamoImplicits._
      val listTablesResult: Future[ListTablesResult] =
        DynamoDb.single(new ListTablesRequest())
      //##simple-request

      listTablesResult.futureValue
    }

    "allow multiple requests - explicit types" in {
      import DynamoImplicits._
      val source = Source
        .single[CreateTable](new CreateTableRequest().withTableName("testTable"))
        .via(DynamoDb.flow)
        .map[DescribeTable](
          result => new DescribeTableRequest().withTableName(result.getTableDescription.getTableName)
        )
        .via(DynamoDb.flow)
        .map(_.getTable.getItemCount)
      val streamCompletion = source.runWith(Sink.seq)
      streamCompletion.failed.futureValue shouldBe a[AmazonDynamoDBException]
    }

    "allow multiple requests" in {
      //##flow
      import DynamoImplicits._
      val source: Source[String, NotUsed] = Source
        .single[CreateTable](new CreateTableRequest().withTableName("testTable"))
        .via(DynamoDb.flow)
        .map(_.getTableDescription.getTableArn)
      //##flow
      val streamCompletion = source.runWith(Sink.seq)
      streamCompletion.failed.futureValue shouldBe a[AmazonDynamoDBException]
    }

    "allow multiple requests - single source" in {
      import DynamoImplicits._
      val source: Source[lang.Long, NotUsed] = DynamoDb
        .source(new CreateTableRequest().withTableName("testTable")) // creating a source from a single req is common enough to warrant a utility function
        .map[DescribeTable](result => new DescribeTableRequest().withTableName(result.getTableDescription.getTableName))
        .via(DynamoDb.flow)
        .map(_.getTable.getItemCount)
      val streamCompletion = source.runWith(Sink.seq)
      streamCompletion.failed.futureValue shouldBe a[AmazonDynamoDBException]
    }

    "provide a paginated requests example" in {
      import DynamoImplicits._

      //##paginated
      val scanPages: Source[ScanResult, NotUsed] =
        DynamoDb.source(new ScanRequest().withTableName("testTable"))
      //##paginated
      val streamCompletion = scanPages.runWith(Sink.seq)
      streamCompletion.failed.futureValue shouldBe a[AmazonDynamoDBException]
    }
  }
}
