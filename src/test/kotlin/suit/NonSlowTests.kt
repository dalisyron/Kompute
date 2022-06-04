package suit

import TestStateConfig
import org.junit.experimental.categories.Categories
import org.junit.runner.RunWith
import org.junit.runners.Suite
import stochastic.TestDTMCCreator
import stochastic.PerformanceTests
import stochastic.policy.SlowTests
import stochastic.StochasticPolicyTest
import stochastic.dtmc.TestIndependentTransitionSymbolsCalculator

@RunWith(Categories::class)
@Categories.ExcludeCategory(SlowTests::class)
@Suite.SuiteClasses(
    StochasticPolicyTest::class,
    TestDTMCCreator::class,
    TestStateConfig::class,
    TestIndependentTransitionSymbolsCalculator::class
)
class NonSlowTestsSuite

@RunWith(Categories::class)
@Categories.IncludeCategory(SlowTests::class)
@Suite.SuiteClasses(
    StochasticPolicyTest::class,
    TestDTMCCreator::class,
    TestStateConfig::class,
    TestIndependentTransitionSymbolsCalculator::class
)
class SlowTestsSuite

@RunWith(Categories::class)
@Categories.IncludeCategory(PerformanceTests::class)
@Suite.SuiteClasses(
    stochastic.PerformanceTests::class
)
class PerformanceTestsSuite