package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.util

import com.intellij.psi.{PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{
  ScBlock,
  ScExpression,
  ScFunctionExpr,
  ScUnderScoreSectionUtil
}
import org.jetbrains.plugins.scala.lang.psi.types.api.PartialFunctionType
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUElement,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.declarations.ScULambdaParameter
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.plugins.scala.util.SAMUtil._
import org.jetbrains.uast.{
  UExpression,
  ULambdaExpression,
  ULambdaExpressionAdapter,
  UParameter
}

import scala.collection.JavaConverters._

trait ScUGenLambda
    extends ULambdaExpressionAdapter
    with ScUElement
    with ScUAnnotated {

  protected def body: Option[PsiElement]
  protected def isExplicitLambda: Boolean

  override def getBody: UExpression =
    body
      .collect {
        case block: ScBlock => block.convertToUExpressionOrEmpty(parent = this)
        case expressionBody =>
          ScUImplicitBlockExpression.convertAndWrapIntoBlock(
            expressionBody,
            LazyUElement.just(this),
            convertLambdas = isExplicitLambda
          )
      }
      .getOrElse(
        BaseScala2UastConverter
          .createUEmptyExpression(element = null, parent = this)
      )

  // TODO: remove
  override def asLogString(): String = toString

  override def toString: String =
    Option(scElement).flatMap(e => Option(e.getText)).orNull
}

trait ScULambdaCommon extends ScUGenLambda with ScUExpression {
  @Nullable
  override def getFunctionalInterfaceType: PsiType =
    scExpression.samTypeParent
      .map(
        cls =>
          //noinspection ScalaWrongMethodsUsage
          PsiType.getTypeByName(
            cls.getQualifiedName,
            scExpression.getProject,
            scExpression.getResolveScope
        )
      )
      .orNull
}

/**
  * [[ScFunctionExpr]] adapter for the [[ULambdaExpression]]
  *
  * @param scExpression Scala PSI element representing explicit
  *                     lambda expression (e.g. `x => x + 1`)
  */
class ScULambdaExpression(override protected val scExpression: ScFunctionExpr,
                          override protected val parent: LazyUElement)
    extends ScULambdaCommon {

  override protected def body: Option[PsiElement] = scExpression.result
  override protected def isExplicitLambda: Boolean = true

  override def getValueParameters: util.List[UParameter] =
    scExpression.parameters.flatMap(_.convertTo[UParameter](this)).asJava
}

/**
  * [[ScBlock]] adapter for the [[ULambdaExpression]]
  *
  * @param scExpression Scala PSI element representing
  *                     partial lambda expression, e.g.
  *                     {{{
  *                       {
  *                         case 1 => ...
  *                         case _ => ...
  *                       }
  *                     }}}
  */
class ScUPartialLambdaExpression(override protected val scExpression: ScBlock,
                                 override protected val parent: LazyUElement)
    extends ScULambdaCommon {

  override protected def body: Option[PsiElement] =
    scExpression.getChildren.collectFirst { case cc: ScCaseClauses => cc }
  override protected def isExplicitLambda: Boolean = true

  override def getValueParameters: util.List[UParameter] = {
    val paramPsiType = scExpression
      .`type`()
      .toOption match {
      case Some(PartialFunctionType(_, paramType)) => paramType.toPsiType
      case _                                       => createUErrorType()
    }

    Seq(
      new ScULambdaParameter(
        name = "<anonymous>",
        paramPsiType,
        declarationScope = scExpression,
        sourcePsi = None,
        LazyUElement.just(this)
      ): UParameter
    ).asJava
  }
}

/**
  * [[ScExpression]] adapter for the [[ULambdaExpression]]
  *
  * @param scExpression Scala PSI expression representing
  *                     method value expression
  */
class ScUMethodValueLambdaExpression(
  override protected val scExpression: ScExpression,
  override protected val parent: LazyUElement
) extends ScULambdaCommon {

  @Nullable
  override def getSourcePsi: PsiElement = null

  override protected def body: Option[PsiElement] = Some(scExpression)
  override protected def isExplicitLambda: Boolean = false

  override def getValueParameters: util.List[UParameter] = scExpression match {
    case MethodValue(psiMethod) =>
      psiMethod.getParameterList.getParameters
        .map(
          new ScULambdaParameter(_, sourcePsi = None, LazyUElement.just(this)): UParameter
        )
        .toSeq
        .asJava
    case _ => JavaCollectionsCommon.newEmptyJavaList
  }
}

/**
  * [[ScExpression]] adapter for the [[ULambdaExpression]]
  *
  * @param scExpression Scala PSI expression representing
  *                     lambda with underscore sections
  */
class ScUUnderscoreLambdaExpression(
  override protected val scExpression: ScExpression,
  override protected val parent: LazyUElement
) extends ScULambdaCommon {

  @Nullable
  override def getSourcePsi: PsiElement = scExpression

  override protected def body: Option[PsiElement] = Some(scExpression)
  override protected def isExplicitLambda: Boolean = false

  override def getValueParameters: util.List[UParameter] =
    ScUnderScoreSectionUtil
      .underscores(scExpression)
      .zipWithIndex
      .map {
        case (us, ind: Int) =>
          new ScULambdaParameter(
            name = s"_$$$ind",
            psiType = us.uastType(),
            declarationScope = scExpression,
            sourcePsi = None,
            LazyUElement.just(this)
          ): UParameter
      }
      .asJava
}
