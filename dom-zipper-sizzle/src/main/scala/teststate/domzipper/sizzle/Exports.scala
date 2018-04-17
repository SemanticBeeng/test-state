package teststate.domzipper.sizzle

import teststate.domzipper.DomZipperJS.CssSelEngine

trait Exports
  extends teststate.domzipper.Exports {

  type Sizzle = teststate.domzipper.sizzle.Sizzle.type
  val Sizzle = teststate.domzipper.sizzle.Sizzle

  implicit val cssSelEngine: CssSelEngine =
    CssSelEngine(Sizzle(_, _).toVector)
}

object Exports extends Exports
