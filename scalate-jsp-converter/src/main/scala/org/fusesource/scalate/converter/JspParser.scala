package org.fusesource.scalate.converter

import org.fusesource.scalate.TemplateException
import util.parsing.input.{Positional, CharSequenceReader, NoPosition, Position}

sealed abstract class PageFragment extends Positional {
}

case class QualifiedName(prefix: String, name: String) extends Positional {
  val qualifiedName = prefix + ":" + name

  override def toString = qualifiedName

}

case class Text(value: String) extends Positional {
  def +(other: String) = Text(value + other).setPos(pos)

  def +(other: Text) = Text(value + other.value).setPos(pos)

  def replaceAll(x: String, y: String) = Text(value.replaceAll(x, y)).setPos(pos)

  def isEmpty = value.length == 0
  
  override def toString = value
}

case class Attribute(name: String, value: String) extends Positional

case class CommentFragment(comment: Text) extends PageFragment
case class DollarExpressionFragment(code: Text) extends PageFragment

case class TextFragment(text: Text) extends PageFragment {
  override def toString = text.toString
}

case class Element(qname: QualifiedName, attributes: List[Attribute], body: List[PageFragment]) extends PageFragment {
  val qualifiedName = qname.qualifiedName
  lazy val attributeMap: Map[String, String] = Map(attributes.map(a => a.name -> a.value): _*)
}


/**
 * Parser of JSP for the purposes of transformation to Scalate; so its not perfect but gives folks a head start
 *
 * @version $Revision : 1.1 $
 */
class JspParser extends MarkupScanner {
  private def phraseOrFail[T](p: Parser[T], in: String): T = {
    var x = phrase(p)(new CharSequenceReader(in))
    x match {
      case Success(result, _) => result
      case NoSuccess(message, next) => throw new InvalidJspException(message, next.pos);
    }
  }

  def parsePage(in: String) = {
    phraseOrFail(page, in)
  }

  def text(p1: Parser[String]): Parser[Text] = {
    positioned(p1 ^^ {Text(_)})
  }

  def upto[T](p: Parser[T]): Parser[Text] = {
    text(
      text("""\z""".r) ~ failure("end of file") ^^ {null} |
              guard(p) ^^ {_ => ""} |
              rep1(not(p) ~> ".|\r|\n".r) ^^ {_.mkString("")}
      )
  }


  def page = rep(pageFragment)

  val pageFragment: Parser[PageFragment] = positioned(markup | textFragment)

  val textFragment = upto(markup) ^^ {TextFragment(_)}

  // TODO not including child markup!!
  def elementTextContent = upto(closeElement) ^^ {TextFragment(_)}
  def elementContent = upto(closeElement) ^^ {case t => if (t.isEmpty) Nil else List(TextFragment(t))}

/*
  def elementTextContent = upto(closeElement | markup) ^^ {TextFragment(_)}

  def elementContent: Parser[List[PageFragment]] = (rep(markup) ~ elementTextContent ~
          ((markup ~ rep(elementContent)) | guard(closeElement))) ^^
          {case m ~ t ~ m2 ~ e2 ~ ce => m :: t :: om :: oe :: Nil }
*/

  def markup: Parser[PageFragment] = element | emptyElement

  def emptyElement = (openElement <~ "/>") ^^
          {case q ~ al => Element(q, al, Nil)}

  def element = ((openElement <~ (repS ~ ">")) ~ elementContent ~ closeElement) ^^
          {case q ~ al ~ b ~ q2 =>
            if (q != q2) throw new InvalidJspException("Expected close element of " + q + " but found " + q2, q2.pos)
            Element(q, al, b)}

  def qualifiedName = positioned(((IDENT <~ ":") ~ IDENT) ^^ {case p ~ n => QualifiedName(p, n)})

  def openElement = ("<" ~> qualifiedName) ~ attributeList

  def closeElement: Parser[QualifiedName] = ("</" ~> qualifiedName) <~ ">"

  def attributeList = rep(attribute)

  def attribute = ((S ~> IDENT <~ repS <~ "=" <~ repS) ~ STRING) ^^ {case n ~ v => Attribute(n, v)}
}

class InvalidJspException(val brief: String, val pos: Position = NoPosition) extends TemplateException(brief + " at " + pos)