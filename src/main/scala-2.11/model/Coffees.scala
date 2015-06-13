package model

import slick.driver.H2Driver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}

// A model.Coffees table with 5 column: name, suppliers id, price, sales, total
class Coffees(tag: Tag) extends Table[(String, Int, Double, Int, Int)](tag, "COFFEES") {

  def name: Rep[String] = column[String]("COF_NAME", O.PrimaryKey)

  def supId: Rep[Int] = column[Int]("SUP_ID")

  def price: Rep[Double] = column[Double]("PRICE")

  def sales: Rep[Int] = column[Int]("SALES")

  def total: Rep[Int] = column[Int]("TOTAL")

  // A reified foreign key relation that can be navigated to create a join
  def supplier: ForeignKeyQuery[Suppliers, (Int, String, String, String, String, String)] =
    foreignKey("SUP_FK", supId, TableQuery[Suppliers])(_.id)

  override def * : ProvenShape[(String, Int, Double, Int, Int)] = (name, supId, price, sales, total)
}
