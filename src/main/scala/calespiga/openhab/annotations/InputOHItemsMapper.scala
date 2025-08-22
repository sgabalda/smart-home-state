package calespiga.openhab.annotations

import scala.quoted.*
import calespiga.model.Event.EventData

object InputOHItemsMapper {

  inline def generateItemsMap(): List[(String, String => EventData)] =
    ${ generateItemsMapImpl }

  private def generateItemsMapImpl(using
      Quotes
  ): Expr[List[(String, String => EventData)]] =
    import quotes.reflect.*

    def recursiveCheck(sym: quotes.reflect.Symbol): List[(Symbol, String)] =
      sym.declaredTypes.flatMap { inner =>
        inner.annotations.flatMap { annotation =>
          annotation match {
            case Apply(
                  Select(New(TypeIdent("InputEventOHItem")), _),
                  List(Literal(StringConstant(itemName)))
                ) =>
              List((inner, itemName))
            case _ =>
              List.empty
          }
        } ++ recursiveCheck(inner)
      }
    val symbol = Symbol.spliceOwner.owner.owner
    val annotatedClasses = recursiveCheck(symbol)

    val mapEntries = annotatedClasses.map { (cls, topic) =>
      val classType = cls.typeRef
      val typeTree = TypeTree.of(using classType.asType)
      val constructor = cls.primaryConstructor

      // Get constructor parameter types
      val paramTypes = constructor.paramSymss.flatten.map(_.tree).collect {
        case vd: ValDef => vd.tpt.tpe
      }

      def getNewExpr[T](convertedValueExpr: Expr[String => T])(using Type[T]) =
        '{ (valueStr: String) =>
          val convertedValue = ${ convertedValueExpr }(valueStr)
          ${
            New(typeTree)
              .select(constructor)
              .appliedToArgs(List('convertedValue.asTerm))
              .asExprOf[EventData]
          }
        }

      // For case classes with exactly one parameter
      val newExpr = paramTypes match {
        case List(paramType) =>
          paramType match {
            case tpe if tpe =:= TypeRepr.of[Boolean] =>
              val convertedValueExpr = '{ (valueStr: String) =>
                valueStr.toBoolean
              }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[Double] =>
              val convertedValueExpr = '{ (valueStr: String) =>
                valueStr.toDouble
              }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[Float] =>
              val convertedValueExpr = '{ (valueStr: String) =>
                valueStr.toFloat
              }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[Int] =>
              val convertedValueExpr = '{ (valueStr: String) => valueStr.toInt }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[Long] =>
              val convertedValueExpr = '{ (valueStr: String) =>
                valueStr.toLong
              }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[String] =>
              val convertedValueExpr = '{ (valueStr: String) => valueStr }
              getNewExpr(convertedValueExpr)

            case tpe if tpe =:= TypeRepr.of[calespiga.model.Switch.Status] =>
              val convertedValueExpr = '{ (valueStr: String) =>
                calespiga.model.Switch.statusFromString(valueStr)
              }
              getNewExpr(convertedValueExpr)

            case _ =>
              report.errorAndAbort(
                s"Unsupported parameter type: ${paramType.show}"
              )
          }

        case _ =>
          report.errorAndAbort("Case classes must have exactly one parameter")
      }
      '{
        ${ Expr(topic) } -> ${ newExpr }
      }
    }

    def liftList[A: Type](list: List[Expr[A]])(using Quotes): Expr[List[A]] = {
      list.foldRight('{ Nil }: Expr[List[A]]) { (expr, acc) =>
        '{ ${ expr } :: ${ acc } }
      }
    }

    liftList(mapEntries)
}
