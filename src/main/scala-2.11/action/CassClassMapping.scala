package action

import slick.driver.H2Driver.api._
import slick.lifted.ProvenShape

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global

object CassClassMapping extends App {

  // The base query for the Users table.
  val users = TableQuery[Users]

  val db = Database.forConfig("h2mem1")

  try {
    // DBIO.seq{}: a sequence operations; return: DBIO[Unit]
    val setupAction: DBIO[Unit] = DBIO.seq(
      // Create the schema.
      users.schema.create,

      // Insert three User instances by two ways:
      // 1.Single row : '+='
      users += User("Jau"),
      // 2.Multi row : '++='
      users ++= Seq(User("Sean"), User("Allen")),

      // Print users (SELECT * FROM USERS)
      users.result.map(println))

    // Only db.run() is real operation.
    val setupFuture: Future[Unit] = db.run(setupAction)


    Await.result(setupFuture, Duration.Inf)
  } finally db.close()

}

// Id's type is Option[Int], because :
// 1.Can be null.
// 2.Usually don't pass Id param, it's auto...
// 3.Id's place is the last one, so we can't need to pass the param.
case class User(name: String, Id: Option[Int] = None)

class Users(tag: Tag) extends Table[User](tag, "USERS") {
  // Auto increment the id primary key column.
  def id: Rep[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  // The name can not be null. Can be null : String instead of Option[String]
  def name: Rep[String] = column[String]("NAME")

  // The '*' projection (e.g. SELECT * ...) auto-transforms the tupled column values to/from a User.
  override def * : ProvenShape[User] = (name, id.?) <> (User.tupled, User.unapply)
}