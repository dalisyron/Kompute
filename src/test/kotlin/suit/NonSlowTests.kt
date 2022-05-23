package suit

import StateConfigTest
import org.junit.experimental.categories.Categories
import org.junit.runner.RunWith
import org.junit.runners.Suite
import stochastic.DTMCCreatorTests
import stochastic.PerformanceTests
import stochastic.SlowTests
import stochastic.StochasticPolicyTest
import stochastic.dtmc.IndependentTransitionCalculatorTest

@RunWith(Categories::class)
@Categories.ExcludeCategory(SlowTests::class)
@Suite.SuiteClasses(
    StochasticPolicyTest::class,
    DTMCCreatorTests::class,
    StateConfigTest::class,
    IndependentTransitionCalculatorTest::class
)
class NonSlowTestsSuite

@RunWith(Categories::class)
@Categories.IncludeCategory(SlowTests::class)
@Suite.SuiteClasses(
    StochasticPolicyTest::class,
    DTMCCreatorTests::class,
    StateConfigTest::class,
    IndependentTransitionCalculatorTest::class
)
class SlowTestsSuite

@RunWith(Categories::class)
@Categories.IncludeCategory(PerformanceTests::class)
@Suite.SuiteClasses(
    stochastic.PerformanceTests::class
)
class PerformanceTestsSuite