package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScLiteralType private(val value: ScLiteral.Value[_],
                                  val allowWiden: Boolean)
                                 (implicit project: Project)
  extends api.ValueType with LeafType {

  override implicit def projectContext: ProjectContext = project

  override def visitType(visitor: api.TypeVisitor): Unit = visitor.visitLiteralType(this)

  def wideType: ScType = value.wideType

  def widen: ScType = if (allowWiden) wideType else this

  def blockWiden: ScLiteralType = if (allowWiden) ScLiteralType(value, allowWiden = false) else this

  override def equals(obj: Any): Boolean = obj match {
    case other: ScLiteralType => value == other.value
    case _ => false
  }

  override def hashCode: Int = value.hashCode
}

object ScLiteralType {

  import ScLiteral.Value

  def apply(value: Value[_],
            allowWiden: Boolean = true)
           (implicit project: Project) =
    new ScLiteralType(value, allowWiden)

  def unapply(literalType: ScLiteralType): Some[(Value[_], Boolean)] =
    Some(literalType.value, literalType.allowWiden)

  // TODO supposed to be polymorphic
  def inferType[E <: ScalaPsiElement](literal: ScLiteral,
                                      allowWiden: Boolean = true): result.TypeResult = {
    implicit val project: Project = literal.getProject
    val literalValue = literal match {
      case _: ScNullLiteral => ScNullLiteral.Value
      case ScIntegerLiteral(value) => ScIntegerLiteral.Value(value)
      case ScLongLiteral(value) => ScLongLiteral.Value(value)
      case ScFloatLiteral(value) => ScFloatLiteral.Value(value)
      case ScDoubleLiteral(value) => ScDoubleLiteral.Value(value)
      case ScBooleanLiteral(value) => ScBooleanLiteral.Value(value)
      case ScSymbolLiteral(value) => ScSymbolLiteral.Value(value)
      case ScCharLiteral(value) => ScCharLiteral.Value(value)
      case ScLiteral(value) => ScLiteral.StringValue(value)
      case _ => null
    }

    literalValue match {
      case null => result.Failure(ScalaBundle.message("wrong.psi.for.literal.type", literal.getText))
      case ScNullLiteral.Value => Right(literalValue.wideType)
      case _ => Right(ScLiteralType(literalValue, allowWiden))
    }
  }

  def widenRecursive(`type`: ScType): ScType = {
    import api._
    import recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

    def isSingleton(param: ScTypeParam) = param.upperBound.exists {
      _.conforms(Singleton(param.projectContext))
    }

    def widenRecursiveInner(`type`: ScType, visited: Set[ParameterizedType]): ScType = `type`.recursiveUpdate {
      case literalType: ScLiteralType => ReplaceWith(literalType.widen)
      case parameterizedType@ParameterizedType(oldDesignator@designator.ScDesignatorType(definition: ScTypeDefinition), typeArguments) if !visited(parameterizedType) =>
        val newDesignator = widenRecursiveInner(oldDesignator, visited + parameterizedType)

        val newArgs = definition.typeParameters
          .zip(typeArguments)
          .map {
            case (param, arg) if isSingleton(param) => arg
            case (_, arg) => widenRecursiveInner(arg, visited + parameterizedType)
          }

        ReplaceWith(ScParameterizedType(newDesignator, newArgs))
      case _: ParameterizedType |
           _: ScCompoundType => Stop
      case _ => ProcessSubtypes
    }

    widenRecursiveInner(`type`, Set.empty)
  }
}