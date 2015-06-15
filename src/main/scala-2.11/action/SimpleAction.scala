package action

import model.{ Coffees, Suppliers }
import slick.backend.DatabasePublisher
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

    } flatMap { _ =>

      /* Streaming */

      val coffeesNamesAction: StreamingDBIO[Seq[String], String] = coffees.map(_.name).result

      val coffeesNamesPublisher: DatabasePublisher[String] = db.stream(coffeesNamesAction)

      coffeesNamesPublisher.foreach(println)

    } flatMap { _ =>

      /* Filtering | Where */

      // Construct a query where the price of coffees is > 9.0
      val filterQuery: Query[Coffees, (String, Int, Double, Int, Int), Seq] = coffees.filter(_.price > 9.0)

      // Print the SQL for the filter query
      println("Generated SQL for filter query:\n" + filterQuery.result.statements)

      // Execute the query and print the Seq of result
      db.run(filterQuery.result.map(println))

    } flatMap { _ =>

      /* Update */

      // Construct an update query with the sales column being the one to update
      val updateQuery: Query[Rep[Int], Int, Seq] = coffees map(_.sales)

      val updateAction: DBIO[Int] = updateQuery.update(1)

      // Print the SQL for the Coffees update query
      println("Generated SQL for Coffees update:\n" + updateQuery.updateStatement)

      // Perform the update
      db.run(updateAction map { numUpdateRows =>
        println(s"Updated $numUpdateRows rows")
      })

    } flatMap { _ =>

      /* Delete */

      // Construct a delete query that deletes coffees with a price less than 8.0
      val deleteQuery: Query[Coffees, (String, Int, Double, Int, Int), Seq] = coffees.filter(_.price < 8.0)

      val deleteAction = deleteQuery.delete

      // Print the SQL for the Coffees delete query
      println("Generated SQL for Coffees delete:\n" + deleteAction.statements)

      db.run(deleteAction map { deleteNumRows =>
        println(s"Deleted $deleteNumRows rows")
      })

    } flatMap { _ =>

      /* Sorting | Order By */

      val sortByPriceQuery: Query[Coffees, (String, Int, Double, Int, Int), Seq] = coffees.sortBy(_.price)

      println("Generated SQL for query sorted by price:\n" + sortByPriceQuery.result.statements)

      db.run(sortByPriceQuery.result.map(println))

    } flatMap { _ =>

      /* Query Composition */

      val composedQuery: Query[Rep[String], String, Seq] = coffees.sortBy(_.name).take(3).filter(_.price > 9.0).map(_.name)

      println("Generated SQL for composed query:\n" + composedQuery.result.statements)

      db.run(composedQuery.result.map(println))

    } flatMap { _ =>

      /* Joins */

      // Join the tables using the relationship defined in the Coffees table
      val joinQuery: Query[(Rep[String], Rep[String]), (String, String), Seq] = for {
        c <- coffees if c.price > 9.0
        s <- c.supplier
      } yield (c.name, s.name)

      println("Generated SQL for the join query:\n" + joinQuery.result.statements)

      // Print the rows which contain the coffee name and the supplier name
      db.run(joinQuery.result.map(println))

    } flatMap { _ =>

      /* Computed Values */

      // Create a new computed column that calculates the max price
      val maxPriceColumn: Rep[Option[Double]] = coffees.map(_.price).max

      println("Generated SQL for max price column:\n" + maxPriceColumn.result.statements)

      db.run(maxPriceColumn.result.map(println))

    } flatMap { _ =>

      /* Manual SQL | String Interpolation */

      // A value to insert into the statement
      val state = "CA"

      // Construct a SQL statement manually with an interpolated value
      val plainQuery = sql"SELECT SUP_NAME FROM SUPPLIERS WHERE STATE = $state".as[String]

      println("Generated SQL for plain query:\n" + plainQuery.statements)

      db.run(plainQuery.map(println))
    }

    Await.result(f, Duration.Inf)

  } finally {
    db.close()
  }
}