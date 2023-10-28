# Kompute: Optimal Task Offloading in Edge Computing

## Abstract

Edge computing aims to bring computing resources closer to the network edge, offering benefits such as reduced response times, lower power consumption, and enhanced mobility management. One of the significant challenges in this domain is the development of efficient task offloading policies, given the diverse nature of user tasks. "Kompute" is a software framework hosted in this repository, designed to determine the optimal task offloading policy for a given system. It utilizes Discrete-time Markov Chains and linear programming for this purpose. This project extends [1], introducing support for heterogeneous tasks.

[1] J. Liu, Y. Mao, J. Zhang, and K. B. Letaief, "Delay-optimal computation task scheduling for mobile-edge computing systems," 2016 IEEE International Symposium on Information Theory (ISIT), Barcelona, Spain, 2016, pp. 1451-1455, doi: 10.1109/ISIT.2016.7541539.

**Keywords**: Task Offloading, Edge Computing, Markov Chains, Linear Programming, Cloud Computing

## Features

- Employs Discrete-time Markov Chains to model the offloading system.
- Utilizes linear programming to ascertain the optimal task offloading policy.
- Simulates the effectiveness of the determined optimal policy.
- Developed in Kotlin, leveraging its concise syntax, extensive standard library, and Java compatibility.
- Supports heterogeneous task offloading strategies.

## Architecture

Below is a class diagram describing the architecture of Kompute:

![Class Diagram](https://github.com/dalisyron/Kompute/assets/34644374/9684542c-5860-4048-97dd-17a5a243b50e)

Kompute tackles the linear programming (LP) optimization problem using the Google Linear Optimization Package (GLOP) solver from the OR-Tools project. It also provides the flexibility to integrate other solvers like CPLEX. The diagram below illustrates the various components utilized in solving the optimization problem to find the optimal offloading policies:

![Linear Solver](https://github.com/dalisyron/Kompute/assets/34644374/46921693-a72a-4da7-8633-fb389542e365)

## Sample Program

The following program demonstrates the use of Kompute to determine the optimal offloading policy for an edge computing environment with two types of tasks: _heavy_ and _light_. The heavy tasks demand considerably more CPU time compared to the light tasks, a scenario typical in IoT devices. For instance, in a smart traffic camera, the task of license plate detection is a heavy task, whereas in a smart air conditioner, the task of checking if the temperature has surpassed a predefined threshold is a light task.

```kotlin
fun main(args: Array<String>) {
    val systemConfig = OffloadingSystemConfig(
        userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 5,
                tuNumberOfPackets = listOf(1, 3),
                cpuNumberOfSections = listOf(7, 2),
                numberOfQueues = 2
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = listOf(4.0, 9.0),
                beta = 90.0,
                etaConfig = null,
                pTx = 0.1,
                pLocal = 8.0,
                pMax = 7.1
            )
        ),
        environmentParameters = EnvironmentParameters(
            nCloud = listOf(1, 1),
            tRx = 5.0
        )
    )
    val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(
        baseSystemConfig = systemConfig,
        precision = 10
    )
    /*
    // For multi-threaded execution, use this instead:
    val optimalPolicy = ConcurrentRangedOptimalPolicyFinder(
        baseSystemConfig = systemConfig
    ).findOptimalPolicy(precision = 10, numberOfThreads = 8)
    */
    val decisionProbabilities: Map<StateAction, Double> = optimalPolicy.stochasticPolicyConfig.decisionProbabilities
    println(decisionProbabilities)
}
