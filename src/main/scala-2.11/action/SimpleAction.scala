package action

import model.{ Coffees, Suppliers }
import slick.driver.H2Driver.api._

import scala.collection.Seq
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * Note:
 * 1.This causes a thread pool (and usually also a connection pool) to be created in the background. <br>
 * 2.You should always close the `Database` object.<br>
 * 3.All database calls in Slick are asynchronous.
 *
 * ==Example:==
 *
 * {{{
 *  val db = Database.forConfig("h2mem1")
 *  try {
 *    val f: Future[_] = {
 *     // body of the application.
 *    }
 *    Await.result(f, Duration.Inf)
 *  } finally db.close
 * }}}
 *
 */

//  Main application.
object SimpleAction extends App {
  //    Config DB connection.
  val db = Database.forConfig("h2mem1")

  try {
    // The query interface for the Suppliers table.
    val suppliers: TableQuery[Suppliers] = TableQuery[Suppliers]
    // The query interface for the Coffees table.
    val coffees: TableQuery[Coffees] = TableQuery[Coffees]

    // `DBIO.seq()` will discard the return value, therefor return type is `DBIO[Unit]`.
    // Performs the individual actions in sequence (using andThen), returning () in the end.
    val setupAction: DBIO[Unit] = DBIO.seq(
      // Create the schema by combining the DDLs for the Suppliers and Coffees tables using the query interfaces.
      (suppliers.schema ++ coffees.schema).create,

      // Insert some suppliers
      suppliers += (101, "Acme, Inc.", "99 Market Street", "Groundsville", "CA", "95199"),
      suppliers += (49, "Superior Coffee", "1 Party Place", "Mendocino", "CA", "95460"),
      suppliers += (150, "The High Ground", "100 Coffee Lane", "Meadows", "CA", "93966"))

    val setupFuture: Future[Unit] = db.run(setupAction)

    // Note:
    // 1. `map` and all other combinators(e.g. flatMap, filter) take an implicit `ExecutionContext`
    // 2. Slick uses its own ExecutionContext internally for running blocking database I/O
    //    but it always maintains a clean separation and prevents you from running non-I/O code on it.
    val f = setupFuture flatMap { _ =>
      // Insert action: usually return the result rows, therefore return type is Option[Int]
      val insertAction: DBIO[Option[Int]] = coffees ++= Seq(
        ("Colombian", 101, 7.99, 0, 0),
        ("French_Roast", 49, 8.99, 0, 0),
        ("Espresso", 150, 9.99, 0, 0),
        ("Colombian_Decaf", 101, 8.99, 0, 0),
        ("French_Roast_Decaf", 49, 9.99, 0, 0))

      val insertAndPrintAction: DBIO[Unit] = insertAction map { coffeesInsertResult =>
        // coffeesInsertResult: num of the result rows
        coffeesInsertResult foreach { numRows =>
          println(s"Inserted $numRows into the coffees table")
        }
      }

      // Queries usually start with a `TableQuery` instance.
      val queryAllSuppliersAction: DBIO[Seq[(Int, String, String, String, String, String)]] =
        suppliers.result

      // `>>` == `andThen`: perform the action in order(1 >> 2 >> 3...)
      // Different from `DBIO.seq()`: it don't discard the return value of the second action.
      val combinedAction: DBIO[Seq[(Int, String, String, String, String, String)]] =
        insertAndPrintAction >> queryAllSuppliersAction

      val combinedFuture: Future[Seq[(Int, String, String, String, String, String)]] = db.run(combinedAction)

      combinedFuture map { allSuppliers =>
        allSuppliers foreach (println)
      }
    }

    Await.result(f, Duration.Inf)
  } finally {
    db.close()
  }
}
