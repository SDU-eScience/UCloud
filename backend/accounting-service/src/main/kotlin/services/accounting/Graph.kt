package dk.sdu.cloud.accounting.services.accounting

// c4367170c8952028827b7ce164a90e7cf42741f5

import java.math.BigInteger
import kotlin.math.min

class Graph(
    val vertexCount: Int,
    val adjacent: Array<LongArray>,
    val cost: Array<Array<BigInteger>>,
    val original: Array<BooleanArray>,
    var index: List<Int>,
    var indexInv: HashMap<Int, Int>,
) {
    fun addEdge(source: Int, destination: Int, capacity: Long, flow: Long) {
        adjacent[source][destination] = capacity
        adjacent[destination][source] = flow

        val midway = vertexCount / 2
        if (destination < midway && source < midway) {
            original[source][destination] = true
        }
    }

    fun addEdgeCost(source: Int, destination: Int, edgeCost: BigInteger) {
        cost[source][destination] = edgeCost
        cost[destination][source] = -edgeCost
    }

    /**
     * Finds the BFS-ordered path from [source] to [destination]
     *
     * [parent] is the path found expressed as `parent.get(childId) = parentId`
     *
     * @return false if a path does not exist, otherwise true
     */
    private fun bfs(source: Int, destination: Int, parent: IntArray): Boolean {
        val visited = BooleanArray(vertexCount)
        val queue = ArrayDeque<Int>(vertexCount)
        queue.add(source)
        visited[source] = true

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for ((index, value) in adjacent[node].withIndex()) {
                if (!visited[index] && value > 0) {
                    queue.add(index)
                    visited[index] = true
                    parent[index] = node
                }
            }
        }

        return visited[destination]
    }

    /**
     * Finds the maximum flow from source to destination using the Edmonds-Karp version of the Ford-Fulkerson method.
     *
     * NOTE: The graph is modified by this function. After the function call, the graph represents the residual graph.
     */
    fun maxFlow(source: Int, destination: Int): Long {
        var maxFlow = 0L
        val path = IntArray(vertexCount)
        while (bfs(source, destination, path)) {
            val pathFlow = flow(source, destination, path)
            addFlow(source, destination, path, pathFlow)
            maxFlow += pathFlow
        }
        return maxFlow
    }

    /**
     * Finds the flow capacity along a given path
     */
    private fun flow(source: Int, destination: Int, path: IntArray): Long {
        var flow = Long.MAX_VALUE
        var currentNode = destination
        while (currentNode != source) {
            val nextNode = path[currentNode]
            flow = min(flow, adjacent[nextNode][currentNode])
            currentNode = nextNode
        }

        return flow
    }

    private fun addFlow(source: Int, destination: Int, path: IntArray, flow: Long) {
        var currentNode = destination
        while (currentNode != source) {
            val nextNode = path[currentNode]
            adjacent[nextNode][currentNode] -= flow
            adjacent[currentNode][nextNode] += flow
            currentNode = nextNode
        }
    }

    private fun notVisited(node: Int, path: List<Int>) = path.none { it == node }

    private fun leastExpensivePath(source: Int, destination: Int): Pair<BigInteger, IntArray> {
        val parent = IntArray(vertexCount)
        var minCost = VERY_LARGE_NUMBER
        val queue = ArrayDeque<List<Int>>(8)
        queue.add(listOf(source))

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (path.last() == destination) {
                val pathEntries = (0 until path.size - 1)
                    .map { i -> BigInteger(cost[path[i]][path[i + 1]].toString()) }

                val pathSum = pathEntries.reduce { acc, bigInteger -> acc.add(bigInteger) }

                val pathCost = pathSum.divide(BigInteger.valueOf(pathEntries.size.toLong()))

                if (pathCost < minCost) {
                    minCost = pathCost
                    for (i in (path.size - 1) downTo 1) {
                        parent[path[i]] = path[i - 1]
                    }
                }
                continue
            }

            for ((index, value) in adjacent[path.last()].withIndex()) {
                if (value > 0 && notVisited(index, path)) {
                    val newPath = path + index
                    queue.add(newPath)
                }
            }
        }

        return Pair(minCost, parent)
    }

    fun minCostFlow(source: Int, destination: Int, desiredFlow: Long): Long {
        var actualFlow = 0L
        while (actualFlow < desiredFlow) {
            val (pathCost, path) = leastExpensivePath(source, destination)
            if (pathCost == VERY_LARGE_NUMBER) break

            var flowToApply = flow(source, destination, path)
            flowToApply = min(flowToApply, desiredFlow - actualFlow)
            actualFlow += flowToApply
            addFlow(source, destination, path, flowToApply)
        }
        return actualFlow
    }

    fun toMermaid(): String {
        return mermaid {
            val hasFakeNodes = index.size < vertexCount
            val graphRoot = indexInv.getValue(0)
            if (hasFakeNodes) {
                for (idx in 0 until vertexCount / 2) {
                    if (idx == graphRoot) continue
                    idx.toString().linkTo(((vertexCount / 2) + idx).toString(), lineType = MermaidGraphBuilder.LineType.INVISIBLE)
                }

                for (idx in 0 until (vertexCount / 2) - 1) {
                    idx.toString().linkTo((idx + 1).toString(), lineType = MermaidGraphBuilder.LineType.INVISIBLE)
                }
            } else {
                for (idx in 0 until vertexCount - 1) {
                    idx.toString().linkTo((idx + 1).toString(), lineType = MermaidGraphBuilder.LineType.INVISIBLE)
                }
            }

            for (idx in 0 until vertexCount) {
                val walletId = index.getOrNull(idx)
                val fakeRootId = index.getOrNull(idx - (vertexCount / 2))
                if (walletId == null && fakeRootId == 0) {
                    continue
                }
                node(
                    idx.toString(),
                    if (walletId == 0) {
                        "Root"
                    } else if (walletId == null) {
                        "Fake-root $fakeRootId"
                    } else {
                        "Wallet $walletId"
                    }
                )

                for ((adjIndex, edge) in adjacent[idx].withIndex()) {
                    val isOriginal = original[idx][adjIndex]
                    if (!isOriginal && edge == 0L) continue
                    val lineType = if (isOriginal) {
                        MermaidGraphBuilder.LineType.NORMAL
                    } else {
                        MermaidGraphBuilder.LineType.DOTTED
                    }
                    idx.toString().linkTo(adjIndex.toString(), "${edge}<br>${cost[idx][adjIndex].toString(16)}", lineType = lineType) //  + " (C=0x${cost[idx][adjIndex].toULong().toString(16)})")
                }
            }
        }
    }

    companion object {
        fun create(vertexCount: Int): Graph {
            val adjacent = Array(vertexCount) { LongArray(vertexCount) }
            val cost = Array(vertexCount) { Array<BigInteger>(vertexCount) { BigInteger.ZERO } }
            val original = Array(vertexCount) { BooleanArray(vertexCount) }
            return Graph(vertexCount, adjacent, cost, original, emptyList(), HashMap())
        }

        private val VERY_LARGE_NUMBER = BigInteger.TWO.pow(110)
    }
}

