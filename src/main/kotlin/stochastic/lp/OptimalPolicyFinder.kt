@file:OptIn(ExperimentalTime::class)

package stochastic.lp

import core.EtaGenerator
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withEtaConfig
import core.UserEquipmentStateManager
import core.policy.Action
import core.splitEqual
import core.toCumulative
import core.ue.UserEquipmentState
import java.lang.Integer.min
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ConcurrentRangedOptimalPolicyFinder(
    private val baseSystemConfig: OffloadingSystemConfig,
) {
    init {
        require(baseSystemConfig.eta == null)
    }

    private var optimalPolicy: StochasticOffloadingPolicy? = null

    fun getEtaBatches(numberOfThreads: Int, etaConfigs: List<List<Double>>): List<List<List<Double>>> {
        val batchSizes = etaConfigs.splitEqual(min(numberOfThreads, etaConfigs.size)).map { it.size }

        val etaConfigsShuffled = etaConfigs.shuffled().toList()
        val batchSizeCumulative = batchSizes.toCumulative()

        val etaBatches: MutableList<List<List<Double>>> = mutableListOf()

        batchSizes.forEachIndexed { index, size ->
            val prev = if (index == 0) 0 else batchSizeCumulative[index - 1]
            etaBatches.add(etaConfigsShuffled.subList(prev, prev + size))
        }

        require(etaBatches.map { it.size }.sum() == etaConfigs.size)

        return etaBatches
    }

    fun findOptimalPolicy(
        precision: Int,
        numberOfThreads: Int
    ): StochasticOffloadingPolicy {
        val etaConfigs: List<List<Double>> = EtaGenerator.generate(baseSystemConfig.numberOfQueues, precision)

        val equation4RowsCacheByEtaType = createEquation4Cache(etaConfigs)

        val etaBatches: List<List<List<Double>>> = getEtaBatches(numberOfThreads, etaConfigs)
        val batchSizeCumulative = etaBatches.map { it.size }.toCumulative()

        val counter = AtomicInteger(0)

        val threads = (etaBatches.indices).map {
            thread(start = false) {
                for ((idx, etaConfig) in etaBatches[it].withIndex()) {
                    val cycleIndex = (if (it == 0) 0 else batchSizeCumulative[it - 1]) + idx
                    println("cycle = $cycleIndex of ${etaConfigs.size} | etaConfig = $etaConfig | alpha = ${baseSystemConfig.alpha} | finishedCount = ${counter.get()}")
                    val systemConfigWithEtas = baseSystemConfig.withEtaConfig(etaConfig)
                    try {
                        val (optimalPolicyWithGivenEta, solveTime) = measureTimedValue {
                             RangedOptimalPolicyFinder.findOptimalPolicyWithGivenEta(
                                systemConfigWithEtas,
                                equation4RowsCacheByEtaType
                            )
                        }

                        println("Solved cycle $cycleIndex in $solveTime ms")
                        counter.incrementAndGet()
                        updateOptimalPolicyResult(optimalPolicyWithGivenEta)
                    } catch (e: IneffectivePolicyException) {
                        counter.incrementAndGet()
                        continue
                    }
                }
            }
        }

        threads.forEach {
            it.start()
        }

        threads.forEach {
            it.join()
        }

        if (optimalPolicy == null) {
            throw NoEffectivePolicyFoundException("No effective policy was found for the given system config which has alpha = ${baseSystemConfig.alpha}")
        }

        return optimalPolicy!!
    }

    @Synchronized
    private fun updateOptimalPolicyResult(optimalPolicyWithGivenEta: StochasticOffloadingPolicy) {
        if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy!!.averageDelay) {
            optimalPolicy = optimalPolicyWithGivenEta
        }
    }

    private fun createEquation4Cache(etaConfigs: List<List<Double>>): Map<OffloadingEtaType, List<EquationRow>> {
        val equation4RowsCacheByEtaType: MutableMap<OffloadingEtaType, List<EquationRow>> = mutableMapOf()

        val allEtaTypes: Map<OffloadingEtaType, List<Double>> =
            etaConfigs.associateBy { OffloadingEtaType.fromEtaConfig(it) }
        allEtaTypes.forEach { (etaType, sampleEtaConfig) ->
            val tempConfig = baseSystemConfig.withEtaConfig(sampleEtaConfig)
            equation4RowsCacheByEtaType[etaType] = OffloadingLPCreator(tempConfig).getEquations4V2()
        }

        return equation4RowsCacheByEtaType
    }
}

object RangedOptimalPolicyFinder {

    fun findOptimalPolicy(
        baseSystemConfig: OffloadingSystemConfig,
        precision: Int
    ): StochasticOffloadingPolicy {

        var optimalPolicy: StochasticOffloadingPolicy? = null

        val etaConfigs = EtaGenerator.generate(baseSystemConfig.numberOfQueues, precision)
        require(baseSystemConfig.eta == null)

        var i = 1
        val equation4RowsCacheByEtaType = createEquation4Cache(baseSystemConfig, etaConfigs)

        for (etaConfig in etaConfigs) {
            val systemConfigWithEtas = baseSystemConfig.withEtaConfig(etaConfig)
            println("cycle $i of ${etaConfigs.size} | etaConfig = $etaConfig | alpha = ${baseSystemConfig.alpha}")
            i++
            try {
                val optimalPolicyWithGivenEta: StochasticOffloadingPolicy
                val solveTime = measureTimeMillis {
                     optimalPolicyWithGivenEta = findOptimalPolicyWithGivenEta(systemConfigWithEtas, equation4RowsCacheByEtaType)
                }
                println("solveTime for cycle ${i - 1} = $solveTime ms")

                if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy.averageDelay) {
                    optimalPolicy = optimalPolicyWithGivenEta
                }
            } catch (e: IneffectivePolicyException) {
                continue
            }
        }

        if (optimalPolicy == null) {
            throw NoEffectivePolicyFoundException("No effective policy was found for the given system config which has alpha = ${baseSystemConfig.alpha}")
        }

        return optimalPolicy
    }


    fun findOptimalPolicyWithGivenEta(
        systemConfig: OffloadingSystemConfig,
        equation4RowsCacheByEtaType: Map<OffloadingEtaType, List<EquationRow>>? = null
    ): StochasticOffloadingPolicy {
        require(systemConfig.eta != null)
        val optimalConfig = OffloadingSolver(systemConfig, equation4RowsCacheByEtaType).findOptimalStochasticConfig()

        return StochasticOffloadingPolicy(
            systemConfig = systemConfig,
            stochasticPolicyConfig = optimalConfig
        )
    }

    private fun createEquation4Cache(
        baseSystemConfig: OffloadingSystemConfig,
        etaConfigs: List<List<Double>>
    ): Map<OffloadingEtaType, List<EquationRow>> {
        val equation4RowsCacheByEtaType: MutableMap<OffloadingEtaType, List<EquationRow>> = mutableMapOf()

        val allEtaTypes: Map<OffloadingEtaType, List<Double>> =
            etaConfigs.associateBy { OffloadingEtaType.fromEtaConfig(it) }
        allEtaTypes.forEach { (etaType, sampleEtaConfig) ->
            val tempConfig = baseSystemConfig.withEtaConfig(sampleEtaConfig)
            equation4RowsCacheByEtaType[etaType] = OffloadingLPCreator(tempConfig).getEquations4V2()
        }

        return equation4RowsCacheByEtaType
    }
}

class OffloadingSolver(
    private val systemConfig: OffloadingSystemConfig,
    private val equation4CacheByEtaType: Map<OffloadingEtaType, List<EquationRow>>? = null
) {

    private val allStates: List<UserEquipmentState> = UserEquipmentStateManager.getAllStatesForConfig(systemConfig)

    fun findOptimalStochasticConfig(): StochasticPolicyConfig {
        val offloadingLPCreator = OffloadingLPCreator(systemConfig)
        lateinit var standardLinearProgram: StandardLinearProgram
        lateinit var offloadingLP: OffloadingLinearProgram
        val etaType: OffloadingEtaType = OffloadingEtaType.fromEtaConfig(systemConfig.eta!!)
        val equation4Cache = equation4CacheByEtaType?.get(etaType)

        requireNotNull(equation4Cache)
        offloadingLP = offloadingLPCreator.createOffloadingLinearProgramExcludingEquation4()
        val rows = offloadingLP.standardLinearProgram.rows.toMutableList()
        for (i in allStates.indices) {
            check(rows[2 + systemConfig.numberOfQueues + i] == null)
            rows[2 + systemConfig.numberOfQueues + i] = equation4Cache[i]
        }
        standardLinearProgram = StandardLinearProgram(rows)

        val indexMapping = offloadingLP.indexMapping
        lateinit var solution: LPSolution
        solution = CPLEXSolver.solve(standardLinearProgram)
        if (solution.isAbnormal) {
            throw IneffectivePolicyException("")
        }
        // println("solution = $solution")

        val policyConfig = createPolicyConfig(solution, indexMapping)

        return policyConfig
    }

    private fun getStateActionProbabilities(
        solution: LPSolution,
        indexMapping: OffloadingLPCreator.IndexMapping
    ): Map<StateAction, Double> {
        return solution.variableValues
            .mapIndexed { index, d ->
                indexMapping.stateActionByCoefficientIndex[index]!! to d
            }
            .toMap()
    }

    private fun createPolicyConfig(
        solution: LPSolution,
        indexMapping: OffloadingLPCreator.IndexMapping
    ): StochasticPolicyConfig {
        // checks
        val possibleActionProvider = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        var expectedVariableCount = 0
        for (state in allStates) {
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            expectedVariableCount += possibleActions.size
        }
        check(solution.variableValues.size == expectedVariableCount)
        check(indexMapping.stateActionByCoefficientIndex.size == expectedVariableCount)

        // create
        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()
        val stateActionProbabilities: Map<StateAction, Double> = getStateActionProbabilities(solution, indexMapping)

        stateActionProbabilities.forEach { (key: StateAction, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val queueFullProbability = stateActionProbabilities
            .filter {
                it.key.state.taskQueueLengths.any { it == systemConfig.taskQueueCapacity } && it.key.action != Action.NoOperation
            }
            .values
            .sum()


        val decisions: MutableMap<StateAction, Double> =
            stateActionProbabilities.mapValues { (key: StateAction, stateActionProbability: Double) ->
                val stateProbability = stateProbabilities[key.state]!!
                if (stateProbability == 0.0) {
                    0.0
                } else {
                    stateActionProbability / stateProbability
                }
            }.toMutableMap()

        if (queueFullProbability > (1.0 / allStates.size)) {
            throw IneffectivePolicyException("queueFullProbability = $queueFullProbability | eta = ${systemConfig.eta} | alpha = ${systemConfig.alpha} | averageDelay = ${solution.objectiveValue}")
        }

        for (state in allStates) {
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            for (action in systemConfig.allActions) {
                if (!possibleActions.contains(action)) {
                    check(!stateActionProbabilities.contains(StateAction(state, action)))
                    decisions[StateAction(state, action)] = 0.0
                }
            }
        }

        for (state in allStates) {
            for (action in systemConfig.allActions) {
                check(decisions.contains(StateAction(state, action))) {
                    StateAction(state, action)
                }
            }
        }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            etaConfig = systemConfig.eta!!,
            averageDelay = solution.objectiveValue,
            systemConfig = systemConfig,
            stateProbabilities = stateProbabilities,
            stateActionProbabilities = stateActionProbabilities
        )

    }
}

class IneffectivePolicyException(message: String) : RuntimeException(message)
class NoEffectivePolicyFoundException(message: String) : RuntimeException(message)