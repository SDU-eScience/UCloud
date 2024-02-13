package dk.sdu.cloud.accounting.services.wallets

import java.util.PriorityQueue

class Vertex(
    var edgesTo: IntArray,
    var costs: IntArray
)

class AnnotatedVertex(
    val vertexIndex: Int,
    var distance: Int,
    var previousVertex: Int = -1,
    var previousEdge: Int = -1,
) : Comparable<AnnotatedVertex> {
    override fun compareTo(other: AnnotatedVertex): Int {
        return distance.compareTo(other.distance)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnnotatedVertex

        return vertexIndex == other.vertexIndex
    }

    override fun hashCode(): Int {
        return vertexIndex
    }
}

fun maxUsable(sourceVertex: Int, graph: Array<Vertex>, baseCost: Int = 0): Int {
    val annotated = Array(graph.size) { index ->
        AnnotatedVertex(
            index,
            distance = if (sourceVertex == index) {
                0
            } else {
                Int.MAX_VALUE
            }
        )
    }

    val stack = ArrayDeque<AnnotatedVertex>()
    stack.addAll(annotated)

    while (stack.isNotEmpty()) {
        stack.sort()
        val annotatedVertex = stack.removeFirst()
        val vertex = graph[annotatedVertex.vertexIndex]
        for ((edgeIndex, neighborIndex) in vertex.edgesTo.withIndex()) {
            val cost = vertex.costs[edgeIndex]
            val neighbor = annotated[neighborIndex]
            val visited = stack.none { it.vertexIndex == neighborIndex }
            if (visited) continue

            val newCost = annotatedVertex.distance + cost
            if (newCost < neighbor.distance) {
                neighbor.distance = newCost
                neighbor.previousEdge = edgeIndex
                neighbor.previousVertex = annotatedVertex.vertexIndex
            }
        }
    }

    var minCapacity = Int.MAX_VALUE
    var currentNode = 0
    data class VertexAndEdge(val vertex: Int, val edge: Int)
    val path = ArrayList<VertexAndEdge>()
    while (true) {
        val node = annotated[currentNode]
        if (node.previousVertex == -1 || node.previousEdge == -1) break

        val costOfEdge = graph[node.previousVertex].costs[node.previousEdge]
        if (costOfEdge < minCapacity) {
            minCapacity = costOfEdge
        }

        currentNode = node.previousVertex
        path.add(VertexAndEdge(currentNode, node.previousEdge))
        if (currentNode == sourceVertex) break
    }

    if (path.lastOrNull()?.vertex == sourceVertex) {
        // Build graph here
        val newGraph = Array(graph.size) {
            val old = graph[it]
            Vertex(
                edgesTo = old.edgesTo.copyOf(),
                costs = old.costs.copyOf()
            )
        }

        for (step in path) {
            val vertex = newGraph[step.vertex]
            vertex.costs[step.edge] -= minCapacity

            // Remove edges that become empty
            if (vertex.costs[step.edge] <= 0) {
                vertex.edgesTo = vertex.edgesTo.filterIndexed { index, _ -> index != step.edge }.toIntArray()
                vertex.costs = vertex.costs.filterIndexed { index, _ -> index != step.edge }.toIntArray()
            }
        }

        return maxUsable(sourceVertex, newGraph, minCapacity + baseCost)
    } else {
        return baseCost
    }
}

fun main() {
    val graph: Array<Vertex> = arrayOf(
        Vertex(edgesTo = IntArray(0), costs = IntArray(0)),                             // 0
        Vertex(edgesTo = intArrayOf(0), costs = intArrayOf(2500)),                      // 1
        Vertex(edgesTo = intArrayOf(1), costs = intArrayOf(1200)),                      // 2
        Vertex(edgesTo = intArrayOf(1), costs = intArrayOf(10000)),                     // 3
        Vertex(edgesTo = intArrayOf(2, 2, 2), costs = intArrayOf(334, 333, 333)),       // 4
        Vertex(edgesTo = intArrayOf(4, 2, 3), costs = intArrayOf(1000, 1000, 1000)),    // 5
    )

    println(maxUsable(5, graph))
}