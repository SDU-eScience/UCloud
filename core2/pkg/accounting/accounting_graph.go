package accounting

import (
	"fmt"
	"math"
	"math/big"
	"ucloud.dk/shared/pkg/util/mermaid"
)

var veryLargeNumber = (&big.Int{}).Lsh(big.NewInt(1), 110)

type Graph struct {
	VertexCount    int
	Adjacent       [][]int64           // residual capacities
	Cost           [][]*big.Int        // per-edge costs (signed)
	Original       [][]bool            // marks "original" edges
	VertexToWallet []accWalletId       // vertex -> wallet
	WalletToVertex map[accWalletId]int // wallet -> vertex
}

// Graph builder
// ---------------------------------------------------------------------------------------------------------------------

// NewGraph allocates an empty graph with all matrices zero-initialised.
func NewGraph(vertexCount int) *Graph {
	adj := make([][]int64, vertexCount)
	cost := make([][]*big.Int, vertexCount)
	orig := make([][]bool, vertexCount)

	for i := 0; i < vertexCount; i++ {
		adj[i] = make([]int64, vertexCount)

		cost[i] = make([]*big.Int, vertexCount)
		for j := 0; j < vertexCount; j++ {
			cost[i][j] = big.NewInt(0)
		}

		orig[i] = make([]bool, vertexCount)
	}

	return &Graph{
		VertexCount:    vertexCount,
		Adjacent:       adj,
		Cost:           cost,
		Original:       orig,
		VertexToWallet: nil,
		WalletToVertex: make(map[accWalletId]int),
	}
}

// AddEdge inserts or updates a residual-capacity edge (and its reverse edge).
func (g *Graph) AddEdge(source, destination int, capacity, flow int64) {
	g.Adjacent[source][destination] = capacity
	g.Adjacent[destination][source] = flow

	midway := g.VertexCount / 2
	if source < midway && destination < midway {
		g.Original[source][destination] = true
	}
}

// AddEdgeCost sets a signed cost on the given edge and the opposite sign on the reverse edge.
func (g *Graph) AddEdgeCost(source, destination int, edgeCost *big.Int) {
	g.Cost[source][destination] = (&big.Int{}).Set(edgeCost)
	g.Cost[destination][source] = (&big.Int{}).Neg(edgeCost)
}

// Graph traversal
// ---------------------------------------------------------------------------------------------------------------------

// bfs finds an augmenting path and records it in parent[v] = u.
func (g *Graph) bfs(source, destination int, parent []int) bool {
	for i := range parent {
		parent[i] = -1
	}
	visited := make([]bool, g.VertexCount)
	queue := []int{source}
	visited[source] = true

	for len(queue) > 0 {
		node := queue[0]
		queue = queue[1:]

		for idx, edgeCap := range g.Adjacent[node] {
			if !visited[idx] && edgeCap > 0 {
				visited[idx] = true
				parent[idx] = node
				if idx == destination {
					return true
				}
				queue = append(queue, idx)
			}
		}
	}
	return false
}

// MaxFlow applies Edmonds-Karp and returns the maximum flow value.
func (g *Graph) MaxFlow(source, destination int) int64 {
	var maxFlow int64
	parent := make([]int, g.VertexCount)

	for g.bfs(source, destination, parent) {
		pathFlow := g.pathCapacity(source, destination, parent)
		g.applyFlow(source, destination, parent, pathFlow)
		maxFlow += pathFlow
	}
	return maxFlow
}

// pathCapacity computes the bottleneck capacity on the current parent-encoded path.
func (g *Graph) pathCapacity(source, destination int, parent []int) int64 {
	flow := int64(math.MaxInt64)
	for v := destination; v != source; v = parent[v] {
		u := parent[v]
		if g.Adjacent[u][v] < flow {
			flow = g.Adjacent[u][v]
		}
	}
	return flow
}

// applyFlow augments the residual graph along the given parent path.
func (g *Graph) applyFlow(source, destination int, parent []int, flow int64) {
	for v := destination; v != source; v = parent[v] {
		u := parent[v]
		g.Adjacent[u][v] -= flow
		g.Adjacent[v][u] += flow
	}
}

// Min-cost flow
// ---------------------------------------------------------------------------------------------------------------------

func (g *Graph) notVisited(node int, path []int) bool {
	for _, v := range path {
		if v == node {
			return false
		}
	}
	return true
}

func (g *Graph) leastExpensivePath(source, destination int) (*big.Int, []int) {
	parent := make([]int, g.VertexCount)
	minCost := (&big.Int{}).Set(veryLargeNumber)

	queue := [][]int{{source}}

	for len(queue) > 0 {
		path := queue[0]
		queue = queue[1:]
		last := path[len(path)-1]

		if last == destination {
			// cost = average of edge costs on the path
			sum := big.NewInt(0)
			for i := 0; i < len(path)-1; i++ {
				sum.Add(sum, g.Cost[path[i]][path[i+1]])
			}
			avg := (&big.Int{}).Div(sum, big.NewInt(int64(len(path)-1)))

			if avg.Cmp(minCost) < 0 {
				minCost.Set(avg)
				for i := len(path) - 1; i >= 1; i-- {
					parent[path[i]] = path[i-1]
				}
			}
			continue
		}

		for idx, edgeCap := range g.Adjacent[last] {
			if edgeCap > 0 && g.notVisited(idx, path) {
				clone := append(append([]int(nil), path...), idx)
				queue = append(queue, clone)
			}
		}
	}
	return minCost, parent
}

// MinCostFlow tries to push desiredFlow units and returns the amount actually sent.
func (g *Graph) MinCostFlow(source, destination int, desiredFlow int64) int64 {
	var actualFlow int64

	for actualFlow < desiredFlow {
		pathCost, parent := g.leastExpensivePath(source, destination)
		if pathCost.Cmp(veryLargeNumber) == 0 { // no more paths
			break
		}

		flow := g.pathCapacity(source, destination, parent)
		if remaining := desiredFlow - actualFlow; flow > remaining {
			flow = remaining
		}
		g.applyFlow(source, destination, parent, flow)
		actualFlow += flow
	}
	return actualFlow
}

// Mermaid debug output
// ---------------------------------------------------------------------------------------------------------------------

func (g *Graph) ToMermaid() string {
	return mermaid.Mermaid(func(b *mermaid.Builder) {
		hasOverAllocationNodes := len(g.VertexToWallet) < g.VertexCount
		graphRoot := g.WalletToVertex[0]
		gSize := g.VertexCount

		if hasOverAllocationNodes {
			gSize = g.VertexCount / 2

			for i := 0; i < gSize; i++ {
				if i != graphRoot {
					b.LinkTo(fmt.Sprint(i), fmt.Sprint(i+gSize), "", mermaid.LineInvisible, nil, nil)
				}
			}
		}

		for i := 0; i < gSize-1; i++ {
			b.LinkTo(fmt.Sprint(i), fmt.Sprint(i+1), "", mermaid.LineInvisible, nil, nil)
		}

		for i := 0; i < g.VertexCount; i++ {
			if i < gSize {
				walletId := g.VertexToWallet[i]
				if walletId != 0 {
					b.Node(fmt.Sprint(i), fmt.Sprintf("Wallet %d", walletId), mermaid.ShapeRound, "")
				}
			} else {
				walletId := g.VertexToWallet[i-gSize]
				if walletId != 0 {
					b.Node(fmt.Sprint(i), fmt.Sprintf("Over-allocation %d", walletId), mermaid.ShapeRound, "")
				}
			}

			for adjIndex := 0; adjIndex < len(g.Adjacent[i]); adjIndex++ {
				isOriginal := g.Original[i][adjIndex]
				edge := g.Adjacent[i][adjIndex]
				if isOriginal || edge != 0 {
					lineType := mermaid.LineNormal
					if !isOriginal {
						lineType = mermaid.LineDotted
					}

					b.LinkTo(
						fmt.Sprint(i),
						fmt.Sprint(adjIndex),
						fmt.Sprintf("%d<br>%X", edge, g.Cost[i][adjIndex]),
						lineType,
						nil,
						nil,
					)
				}
			}
		}
	})
}
