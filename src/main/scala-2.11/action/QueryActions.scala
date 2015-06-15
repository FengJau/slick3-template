package action

import slick.driver.H2Driver.api._
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Demonstrates various ways of reading data
object QueryActions extends App {

  // A simple dictionary table with key->value
  class Dict(tag: Tag) extends Table[(Int, String)](tag, "INT_DICT") {
    def key: Rep[Int] = column[Int]("KEY", O.PrimaryKey)
    def value: Rep[String] = column[String]("VALUE")
    override def * : ProvenShape[(Int, String)] = (key, value)
  }

  val dict = TableQuery[Dict]

  val db = Database.forConfig("h2mem1")

  try {

    val setupAction: DBIO[Unit] = DBIO.seq(
      dict.schema.create,
      dict ++= Seq(1 -> "a", 2 -> "b", 3 -> "c", 4 -> "d", 5 -> "e")
    )

    val setupFuture: Future[Unit] = db.run(setupAction)

    // Define a pre-compiled parameterized query for reading all key|value pairs up to a given param.

    val upToQueryFunction = Compiled { param: Rep[Int] =>
      dict.filter(_.key <= param).sortBy(_.key)
    }

    val upToAction = upToQueryFunction(3).result.map{ result =>
      println("Seq (Vector) of k/v pairs up to 3")
      println("- " + result)
    }

    val upToFuture: Future[Unit] = db.run(upToAction)

    // A second pre-compiled query which returns a Set[String]
    val upToSetQueryFunction = upToQueryFunction.map(_.andThen(_.to[Set]))

    val upToSetAction = upToSetQueryFunction(3).result.map { result =>
      println("Set of k/v pairs up to 3")
      println("- " + result)
    }

    val upToSetFuture: Future[Unit] = db.run(upToSetAction)

    // Keys to Array.
    val keysArrayAction = dict.map(_.key).to[Array].result.map { result =>
      println("All keys in an unboxed Array[Int]")
      println("- " + result)
    }

    val keysArrayFuture: Future[Unit] = db.run(keysArrayAction)

    // First result
    val firstResultAction = upToQueryFunction(3).result.head.map { result =>
      println("Only get the first result, failing if there is none")
      println("- " + result)
    }

    val firstResultFuture: Future[Unit] = db.run(firstResultAction)

    // First result - as Option or None
    val firstResultOrElseAction = upToQueryFunction(3).result.headOption.map { r =>
      println("Get the first result as an Option, or None")
      println("- " + r)
    }

    val firstResultOrElseFuture: Future[Unit] = db.run(firstResultOrElseAction)

    val f = setupFuture flatMap(_ => upToFuture) flatMap(_ => upToSetFuture) flatMap(_ => keysArrayFuture) flatMap(_ => firstResultFuture) flatMap(_ => firstResultOrElseFuture)

    Await.result(f, Duration.Inf)

  } finally db.close()
}
