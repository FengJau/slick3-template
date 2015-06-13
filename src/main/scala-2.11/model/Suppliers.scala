package model

import slick.driver.H2Driver.api._
import slick.lifted.ProvenShape

// A suppliers table with 6 columns: id, name, street, city, state, zip
class Suppliers(tag: Tag) extends Table[(Int, String, String, String, String, String)](tag, "SUPPLIERS") {

  // This is the primary key column:

  def id: Rep[Int] = column[Int]("SUP_ID", O.PrimaryKey)

  def name: Rep[String] = column("SUP_NAME")

  def street: Rep[String] = column("STREET")

  def city: Rep[String] = column("CITY")

  def state: Rep[String] = column("STATE")

  def zip: Rep[String] = column("ZIP")

  override def * : ProvenShape[(Int, String, String, String, String, String)] = (id, name, street, city, state, zip)
}
