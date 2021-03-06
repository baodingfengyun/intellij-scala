package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.ScalaTestSingleTestTest

class Scalatest2_10_1_9_2_SelectedTestsTest extends {
  override val featureSpecConfigTestName = "Feature 1 Scenario: Scenario A"
  override val featureSpecTestPath = List("[root]", "Feature 1 Scenario: Scenario A")
  override val featureSpecTaggedConfigTestName = "Feature 3 Scenario: Tagged"
  override val featureSpecTaggedTestPath = List("[root]", "Feature 3 Scenario: Tagged")
  override val freeSpecPathTestPath = List("[root]", "A FreeSpecTest should be able to run single test")
  override val freeSpecTestPath = List("[root]", "A FreeSpecTest should be able to run single tests")
  override val funSpecTestPath = List("[root]", "FunSpecTest should launch single test")
  override val funSpecTaggedTestPath = List("[root]", "taggedScope is tagged")
  override val propSpecTestPath = List("[root]", "Single tests should run")
  override val propSpecTestTaggedPath = List("[root]", "tagged")
  override val funSuiteTestPath = List("[root]", "should run single test")
  override val funSuiteTaggedTestPath = List("[root]", "tagged")
  override val wordSpecTestPath = List("[root]", "WordSpecTest should Run single test")
  override val wordSpecTestTaggedPath = List("[root]", "tagged should be tagged")
} with Scalatest2_10_1_9_2_Base with ScalaTestSingleTestTest {

  override def preprocessSingleFlatSpecExpectedPath(path: Seq[String]): Seq[String] = {
    path.splitAt(2) match {
      case (Seq(root, testClassName), remainingPath) =>
        // drop testClass name intentionally when running single test
        Seq(root, remainingPath.mkString(" "))
    }
  }
}
