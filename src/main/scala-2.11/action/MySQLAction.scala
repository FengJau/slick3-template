package action

import slick.driver.MySQLDriver.api._
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration

// Template for MySQL
object MySQLAction extends App {
  val _users = TableQuery[_Users]

  val db = Database.forConfig("mysql")

  try {

    val queryAction = _users.result.map(println)

    val queryFuture: Future[Unit] = db.run(queryAction)

    val f: Future[Unit] = queryFuture flatMap { _ =>
      val insertAction = _users += _User("allen", "20", Some(8))
      db.run(insertAction)
    } flatMap { _ =>
      val afterInsertQueryAction = _users.result.map(println)
      db.run(afterInsertQueryAction)
    }

    Await.result(f, Duration.Inf)

  } finally db.close()

}

case class _User(userName: String, userSex: String, id: Option[Int] = None)

class _Users(tag: Tag) extends Table[_User](tag, "USER") {
  def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
  def userName: Rep[String] = column[String]("USER_NAME")
  def userSex: Rep[String] = column[String]("USER_SEX")

  override def * : ProvenShape[_User] = (userName, userSex, id.?) <> (_User.tupled, _User.unapply)
}