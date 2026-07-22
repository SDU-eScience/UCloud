// Known faults in the reference implementation, each demonstrated by a
// TestReferenceFault test:
//
//   - Repeated capacity retirement in one allocation group can count already
//     retired flow again. For example, after an old allocation retires and its
//     flow moves to a replacement, retiring the replacement records the entire
//     active group flow instead of subtracting prior retired attribution.
//   - RefAddAllocation ignores its operation timestamp and samples time.Now().
//   - IsActive excludes the exact start and ignores the allocation end instead
//     of implementing the [start, end) validity interval.
//   - A zero-quota retirement marks the allocation retired but leaves its group
//     membership active and skips normal retirement bookkeeping.
//   - Non-capacity monotonicity is absent: the reference accepts decreases that
//     Core rejects because retired periodic attribution cannot be reconciled.
//   - Rootless over-allocation can propagate usage through a parent which has no
//     root entitlement because the reference synthetic-edge model is incomplete.
//   - An allocation already expired when it is created is immediately retired
//     by Core, while the reference cannot retire an allocation it never marked
//     active. This affects historic/generated replay workloads.
//   - PreferredBalance multiplies quota by elapsed time in int64 and overflows
//     for large quotas intentionally supported by Core.
//
// Known limitations which are not asserted as faults:
//
//   - Multi-parent routing is not a stable oracle for Core. The reference uses
//     average-path int64 costs and map-dependent graph indices, while Core uses
//     explicit balance and expiration ordering.
//   - Capacity-retirement rebalancing samples time.Now() instead of the operation
//     timestamp, so replayed routing can differ even when both results are valid.
//   - Negative charges in complex graphs can choose different reflow paths due
//     to the implementations' different graph construction and routing costs.
//   - Scoped counters and Core's absolute-report contract are absent. Tests
//     translate accepted reports into their resulting wallet-local delta.
//   - Allocation commit state, grants, allocation updates, malformed-topology
//     rejection, scoped identity, persistence, and notifications are not modeled.

package accounting

import (
	"bufio"
	"errors"
	"fmt"
	"log"
	"math"
	"os"
	"strconv"
	"strings"
	"time"
)

// graph/graph.go
// =====================================================================================================================

type RefGraph struct {
	V        int
	Adj      [][]int64
	Cost     [][]int64
	Original [][]bool
	// MinFlow  []int64
	Index    []int64
	IndexInv map[int64]int
}

func makeArray[T any](v int) (res [][]T) {
	res = make([][]T, v)
	for i := range res {
		res[i] = make([]T, v)
	}
	return
}

func NewRefGraph(v int) *RefGraph {
	return &RefGraph{
		V:        v,
		Adj:      makeArray[int64](v),
		Cost:     makeArray[int64](v),
		Original: makeArray[bool](v),
		// MinFlow:  make([]int64, v),
	}
}

func (g *RefGraph) AddEdge(u, v int, capacity int64, flow int64) {
	g.Adj[u][v] = capacity
	g.Adj[v][u] = flow
	if v < (g.V/2) && u < (g.V/2) {
		g.Original[u][v] = true
	}
}

func (g *RefGraph) AddEdgeCost(u, v int, w int64) {
	//TODO: check if edge exist
	g.Cost[u][v] = w
	g.Cost[v][u] = -w
}

// func (g *Graph) AddMinFlow(u int, f int64) {
// 	g.MinFlow[u] = f
// }

// finds the BFS-ordered path from s to t
// return false if a path does not exist otherwise true
// parent is the path found expressed as parent[childId] = parentId
func (g *RefGraph) BFS(s, t int, parent []int) bool {
	visited := make([]bool, g.V)
	queue := make([]int, 0, g.V)
	queue = append(queue, s)
	visited[s] = true
	for len(queue) != 0 {
		u := queue[0]
		queue = queue[1:]
		for ind, val := range g.Adj[u] {
			if !visited[ind] && val > 0 {
				queue = append(queue, ind)
				visited[ind] = true
				parent[ind] = u
			}
		}
	}
	return visited[t]
}

// find the maximum flow from s to t using
// the Edmonds-Karp version of the Ford-Fulkerson method
// NB: the graph g is modified by this function
// after the function call, the graph represents the residual graph
func (g *RefGraph) MaxFlow(s, t int) (maxFlow int64) {
	parent := make([]int, g.V)
	for g.BFS(s, t, parent) {
		//find flow along the path
		pathFlow := g.Flow(s, t, parent)
		//apply the flow
		maxFlow += pathFlow
		g.addFlow(s, t, parent, pathFlow)
		// for v := t; v != s; v = parent[v] {
		// 	u := parent[v]
		// 	g.Adj[u][v] -= pathFlow
		// 	g.Adj[v][u] += pathFlow
		// }
	}
	return
}

// update residual graph by adding a flow to the path from s to t
func (g *RefGraph) addFlow(s, t int, parent []int, flow int64) {
	for v := t; v != s; v = parent[v] {
		u := parent[v]
		g.Adj[u][v] -= flow
		g.Adj[v][u] += flow
	}
}

// find flow capacity along the given path
func (g *RefGraph) Flow(start, end int, parent []int) (flow int64) {
	flow = int64(math.MaxInt64)
	for v := end; v != start; v = parent[v] {
		u := parent[v]
		flow = min(flow, g.Adj[u][v])
	}
	return
}

func notVisited(e int, path []int) bool {
	for _, v := range path {
		if e == v {
			return false
		}
	}
	return true
}

// returns the maximum flow along a single path
// finds all paths from start to end and compute the flow along each path
func (g *RefGraph) MaxPathFlow(start, end int) (mpf int64) {
	//queue of partial paths
	queue := make([][]int, 0, 8)

	//create a path with the starting point
	path := make([]int, 0, 8)
	path = append(path, start)

	//push the path on the queue
	queue = append(queue, path)

	for len(queue) != 0 {
		//take a path from the top of the queue
		path = queue[0]
		queue = queue[1:]

		// check if we reach the end vertex
		last := path[len(path)-1]
		if last == end {
			//we found a path
			//calculate flow
			parent := make([]int, g.V)
			for i := len(path) - 1; i > 0; i-- {
				parent[path[i]] = path[i-1]
			}
			mpf = max(mpf, g.Flow(start, end, parent))
			continue
		}

		for ind, val := range g.Adj[last] {
			if val > 0 && notVisited(ind, path) {
				newpath := make([]int, len(path), 8)
				copy(newpath, path)
				newpath = append(newpath, ind)
				queue = append(queue, newpath)
			}
		}
	}

	return
}

// remove all edges which are an original edge so that only the back flow remains
func (g *RefGraph) PruneResidualGraph() {
	for i, list := range g.Original {
		for j, ok := range list {
			if ok {
				g.Adj[i][j] = 0
			}
		}
	}
}

// finds the path of least cost from start to end
// returns the path as a parent list and the cost
func (g *RefGraph) leastExpensivePath(start, end int) (cost int64, parent []int) {
	//this is the cost along the path
	pCost := make([]int64, g.V)
	for i := range pCost {
		pCost[i] = math.MaxInt64
	}
	pCost[start] = 0
	// pCost[start] = math.MaxInt64

	inQueue := make([]bool, g.V)
	queue := make([]int, 0, 8)
	queue = append(queue, start)

	parent = make([]int, g.V)
	// for i := range parent {
	// 	parent[i] = -1
	// }

	for len(queue) != 0 {
		u := queue[0]
		queue = queue[1:]
		inQueue[u] = false
		for v, cap := range g.Adj[u] {
			if cap > 0 && pCost[v] > pCost[u]+g.Cost[u][v] {
				pCost[v] = pCost[u] + g.Cost[u][v]
				// cost := min(g.Cost[u][v], pCost[u])
				// if cap > 0 && pCost[v] > cost {
				// 	pCost[v] = cost
				parent[v] = u
				if !inQueue[v] {
					inQueue[v] = true
					queue = append(queue, v)
				}
			}
		}

	}

	cost = pCost[end]

	return
}

// finds the path of least cost from start to end
// returns the path as a parent list and the cost
type costfn func(*RefGraph, []int) int64

func sumRefCosts(g *RefGraph, path []int) (cost int64) {
	for i := 0; i < len(path)-1; i++ {
		cost += g.Cost[path[i]][path[i+1]]
	}
	return
}

func avrRefCosts(g *RefGraph, path []int) (cost int64) {
	n := len(path) - 1
	for i := 0; i < len(path)-1; i++ {
		cost += g.Cost[path[i]][path[i+1]]
	}
	cost /= int64(n)
	return
}

// returns the maximum flow along a single path
// finds all paths from start to end and compute the flow along each path
func (g *RefGraph) leastExpensivePathGeneric(start, end int, costFn costfn) (cost int64, parent []int) {
	parent = make([]int, g.V)
	cost = math.MaxInt64

	//queue of partial paths
	queue := make([][]int, 0, 8)

	//create a path with the starting point
	path := make([]int, 0, 8)
	path = append(path, start)

	//push the path on the queue
	queue = append(queue, path)

	for len(queue) != 0 {
		//take a path from the top of the queue
		path = queue[0]
		queue = queue[1:]

		// check if we reach the end vertex
		last := path[len(path)-1]
		if last == end {
			//we found a path
			//calculate cost
			pathCost := costFn(g, path)
			if pathCost < cost {
				cost = pathCost
				//update path
				for i := len(path) - 1; i > 0; i-- {
					parent[path[i]] = path[i-1]
				}
			}
			continue
		}

		for ind, val := range g.Adj[last] {
			if val > 0 && notVisited(ind, path) {
				newpath := make([]int, len(path))
				copy(newpath, path)
				newpath = append(newpath, ind)
				queue = append(queue, newpath)
			}
		}
	}

	return
}

func (g *RefGraph) MinCostFlow(s, t int, flow int64, isInverse bool) (actualFlow int64) {
	// var bfsV []int
	// tOrig := t
	for actualFlow < flow {
		// pathCost, parent := g.leastExpensivePath(s, t)
		pathCost, parent := g.leastExpensivePathGeneric(s, t, avrRefCosts)
		if pathCost == math.MaxInt64 {
			break
			// if bfsV == nil {
			// 	if isInverse {
			// 		bfsV = g.bfsOrder(t, s)
			// 	} else {
			// 		bfsV = g.bfsOrder(s, t)
			// 	}
			// }
			// //now we move s to the next in the bfsV list descending towards t
			// if len(bfsV) == 0 {
			// 	break
			// }
			// if isInverse {
			// 	t = bfsV[0]
			// 	bfsV = bfsV[1:]
			// } else {
			// 	s = bfsV[0]
			// 	bfsV = bfsV[1:]
			// }
			// continue
		}
		//find flow along path
		f := g.Flow(s, t, parent)
		//cap flow to requested flow
		f = min(f, flow-actualFlow)
		//apply flow
		actualFlow += f
		// cost += f * pathCost
		g.addFlow(s, t, parent, f)
	}

	// // fix minFlow constraints
	// if isInverse {
	// 	//check for minFlow requests
	// 	t = tOrig //reset t
	// 	bfsV = g.bfsOrder(t, s)
	// 	for len(bfsV) != 0 {
	// 		t = bfsV[0]
	// 		bfsV = bfsV[1:]
	// 		if g.MinFlow[t] != 0 {
	// 			//incoming flow should not be less than minFlow
	// 			flow := g.incomingFlow(t) - g.MinFlow[t]
	// 			if flow >= 0 {
	// 				//constraint is satisfy nothing to do
	// 				continue
	// 			}
	// 			//flow back the min flow
	// 			actualFlow -= g.MinCostFlow(t, s, -flow, false)
	// 		}
	// 	}
	// }

	return
}

// enumerate vertices in bfs order excluding s and t in the original graph
func (g *RefGraph) bfsOrder(s, t int) (vlist []int) {
	visited := make([]bool, g.V)
	queue := make([]int, 0, g.V)
	vlist = make([]int, 0, g.V)
	queue = append(queue, s)
	// vlist = append(vlist, s)
	visited[s] = true
	for len(queue) != 0 {
		u := queue[0]
		queue = queue[1:]
		for ind, val := range g.Original[u] {
			if !visited[ind] && val {
				queue = append(queue, ind)
				if ind != t {
					vlist = append(vlist, ind)
				}
				visited[ind] = true
			}
		}
	}
	return
}

func (g *RefGraph) incomingFlow(s int) (flow int64) {
	for i := 0; i < g.V; i++ {
		if g.Original[s][i] {
			flow += g.Adj[i][s]
		}
	}
	return
}

// wallet/allocations.go
// =====================================================================================================================

type RefAllocation struct {
	Id           int64
	BelongsTo    int64
	ParentWallet int64
	Quota        int64
	Start        time.Time
	End          time.Time
	Retired      bool
	RetiredUsage int64
}

// Global data structure
var GlobalRefAllocations map[int64]*RefAllocation

func GetRefAllocationFromId(id int64) *RefAllocation {
	return GlobalRefAllocations[id]
}

func (a *RefAllocation) Register() (err error) {
	_, ok := GlobalRefAllocations[a.Id]

	if ok {
		errmsg := fmt.Sprintf("A%d: Refusing to register allocation with already existing id\n", a.Id)
		err = errors.New(errmsg)
		return
	}

	GlobalRefAllocations[a.Id] = a

	return
}

func (a *RefAllocation) IsActive(ts time.Time) bool {
	// return !a.retired && TimeIsBetween(ts, a.start, a.end)
	//I think the version above makes th system inconsistent
	return !a.Retired && ts.After(a.Start)
}

func (a *RefAllocation) PreferredBalance(ts time.Time) (balance int64) {
	//this return a linear interpolation of the quota between start and end date

	if a.Retired {
		return 0
	}

	s := a.Start.Unix()
	e := a.End.Unix()
	t := ts.Unix()

	if t <= s {
		return 0
	}

	if e <= t {
		return a.Quota
	}

	//beware of overflows in the integer expression below
	balance = (a.Quota * (t - s)) / (e - s)

	return

}

func (a *RefAllocation) Retire() error {
	return GetRefWalletFromId(a.BelongsTo).Retire(a)
}

// wallet/allocgroup.go
// =====================================================================================================================

type allocRefGroup struct {
	treeUsage          int64
	retiredTreeUsage   int64
	earliestExpiration time.Time
	allocations        map[int64]bool //if true the allocation is active
}

func NewRefAllocGroup() *allocRefGroup {
	return &allocRefGroup{
		allocations: map[int64]bool{},
	}
}

// Is any allocation in this group active?
func (a *allocRefGroup) IsActive() (res bool) {
	for _, isActive := range a.allocations {
		if isActive {
			return true
		}
	}
	return false
}

// sum of quota of active allocations in this group
func (a *allocRefGroup) totalActiveQuota() (res int64) {
	for id, isActive := range a.allocations {
		if !isActive {
			continue
		}
		alloc := GetRefAllocationFromId(id)
		res += alloc.Quota
	}
	return res
}

// sum of preferred balance of all allocations in the group
func (a *allocRefGroup) PreferredBalance(ts time.Time) (res int64) {
	for id, isActive := range a.allocations {
		if !isActive {
			continue
		}
		alloc := GetRefAllocationFromId(id)
		res += alloc.PreferredBalance(ts)
	}
	return res
}

// wallet/checks.go
// =====================================================================================================================

// tree usage of allocation group must match child utilizationin parent wallet
func CheckRefTreeUsageHierarchy() (err error) {
	for id := range GlobalRefWallets {
		w := GetRefWalletFromId(id)
		for pId := range w.AllocationsByParent {
			if pId == 0 {
				//skip root wallet
				continue
			}
			p := GetRefWalletFromId(pId)
			childU := p.childUsage(id)
			treeU := w.treeUsage(pId)
			if treeU != childU {
				msg := fmt.Sprintf("Wrong Usage in W%d for child W%d: found %d instead of %d", pId, id, childU, treeU)
				err = errors.New(msg)
				return
			}
		}
	}
	return
}

// tree usage of allocation group must match child utilization in parent wallet
func CheckRefWalletHierarchyTreeUsage(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		for pId := range w.AllocationsByParent {
			if pId == 0 {
				//skip root wallet
				continue
			}
			p := GetRefWalletFromId(pId)
			childU := p.childUsage(id)
			treeU := w.treeUsage(pId)
			if treeU != childU {
				msg := fmt.Sprintf("Wrong Usage in W%d for child W%d: found %d instead of %d", pId, id, childU, treeU)
				err = errors.New(msg)
				return
			}
		}
	}
	return
}

// active tree usage should never exceed active quota of allocation group
func CheckRefTreeUsageLimit() (err error) {
	for id := range GlobalRefWallets {
		w := GetRefWalletFromId(id)
		for pId, ag := range w.AllocationsByParent {
			quota := ag.totalActiveQuota()
			treeU := ag.treeUsage
			if treeU > quota {
				msg := fmt.Sprintf("Wrong Active Tree Usage in W%d for parent W%d: found %d > %d (active quota)", id, pId, treeU, quota)
				err = errors.New(msg)
				return
			}
		}
	}
	return
}

// active tree usage should never exceed active quota of allocation group
func CheckRefWalletHierarchyTreeUsageLimit(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		for pId, ag := range w.AllocationsByParent {
			quota := ag.totalActiveQuota()
			treeU := ag.treeUsage
			if treeU > quota {
				msg := fmt.Sprintf("Wrong Active Tree Usage in W%d for parent W%d: found %d > %d (active quota)", id, pId, treeU, quota)
				err = errors.New(msg)
				return
			}
		}
	}
	return
}

// check that total active allocated is bigger than active children usage
func CheckRefWalletHierarchyActiveAllocated(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		activeAllocated := w.TotalAllocated
		activeChildrenUsage := int64(0)
		for _, usage := range w.ChildrenUsage {
			activeChildrenUsage += usage

		}
		if activeChildrenUsage > activeAllocated {
			msg := fmt.Sprintf("Wrong active children usage in W%d: found %d > %d (active allocated)", id, activeChildrenUsage, activeAllocated)
			err = errors.New(msg)
			return
		}
	}
	return
}

// check that total retired allocated is bigger than retired children usage
func CheckRefWalletHierarchyAllocatedRetiredUsage(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		retiredAllocated := w.TotalRetiredAllocated
		retiredChildrenUsage := int64(0)
		for _, usage := range w.ChildrenRetiredUsage {
			retiredChildrenUsage += usage

		}
		if retiredChildrenUsage > retiredAllocated {
			msg := fmt.Sprintf("Wrong retired children usage in W%d: found %d > %d (retired allocated)", id, retiredChildrenUsage, retiredAllocated)
			err = errors.New(msg)
			return
		}
	}
	return
}

// check that wallet excess usage does not exceed the overallocation
func CheckRefWalletHierarchyExcessUsage(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		overallocation := w.TotalAllocated + w.TotalRetiredAllocated + w.LocalUsage - w.totalActiveQuota()
		if overallocation <= 0 {
			continue
		}
		// excessUsage := w.TotalUsage() - w.totalTreeUsage()
		excessUsage := w.ExcessUsage
		if excessUsage > overallocation {
			msg := fmt.Sprintf("Wrong Excess Usage in W%d: found %d > %d (overallocation)", id, excessUsage, overallocation)
			err = errors.New(msg)
			return
		}
	}
	return
}

// locally retired must match the sum of allocations retired usage
func CheckRefWalletHierarchyLocalRetiredUsage(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		lR := w.LocalRetiredUsage
		agRetired := int64(0)
		for _, ag := range w.AllocationsByParent {
			for aId, active := range ag.allocations {
				if active {
					continue
				}
				a := GetRefAllocationFromId(aId)
				agRetired += a.RetiredUsage
			}
		}
		if agRetired != lR {
			msg := fmt.Sprintf("Wrong Retired Usage in W%d: found %d != %d (local retired usage)", id, agRetired, lR)
			err = errors.New(msg)
			return
		}

	}
	return
}

// check that each parent retired child usage match the child allocation group retired usage
func CheckRefWalletHierarchyParentRetiredUsage(wId int64) (err error) {
	localWallets, _ := GenerateRefLocalWallets(wId)
	for _, id := range localWallets {
		w := GetRefWalletFromId(id)
		for pId, ag := range w.AllocationsByParent {
			if pId == 0 {
				//skip root wallet
				continue
			}
			p := GetRefWalletFromId(pId)
			childRU := p.ChildrenRetiredUsage[id]
			aRU := ag.retiredTreeUsage
			if aRU != childRU {
				msg := fmt.Sprintf("Wrong Retired Usage in W%d for child W%d: found %d instead of %d", pId, id, childRU, aRU)
				err = errors.New(msg)
				return
			}
		}
	}
	return
}

// do all checks
func CheckRefWalletHierarchy(wId int64) (err error) {
	type errorCheckFn func(int64) error

	checks := []errorCheckFn{
		CheckRefWalletHierarchyTreeUsage,
		CheckRefWalletHierarchyTreeUsageLimit,
		CheckRefWalletHierarchyActiveAllocated,
		CheckRefWalletHierarchyAllocatedRetiredUsage,
		CheckRefWalletHierarchyExcessUsage,
		CheckRefWalletHierarchyLocalRetiredUsage,
		CheckRefWalletHierarchyParentRetiredUsage,
	}

	for _, test := range checks {
		err = test(wId)
		if err != nil {
			return
		}
	}
	return
}

// wallet/importdata.go
// =====================================================================================================================

var RefAllocationLocalUsage map[int64]int64       //for keeping track of local usage
var RefWalletTotalUsage map[int64]int64           //for keeping track of total usage
var RefAllocationParentAllocation map[int64]int64 //for keeping track of allocation path

func fillRefGlobalAllocations(allocations []RefAllocation) {
	GlobalRefAllocations = make(map[int64]*RefAllocation, len(allocations))
	start := time.Date(2023, 1, 1, 0, 0, 0, 0, time.UTC)
	end := start.AddDate(10, 0, 0) //add 10 years
	for i, a := range allocations {
		id := int64(i) + 1
		a.Id = id
		a.Start = start
		a.End = end
		newAllocation := a //make copy
		GlobalRefAllocations[id] = &newAllocation
	}
}

// fill global wallets from global allocations
func fillRefGlobalWallets(isCapacityBased bool) {
	//create global wallets
	GlobalRefWallets = make(map[int64]*RefWallet, 128)
	for _, a := range GlobalRefAllocations {
		wid := a.BelongsTo
		if wid != 0 {
			w := GetRefWalletFromId(wid)
			if w == nil {
				//create new wallet
				w = NewRefWallet(wid, isCapacityBased)
				//add it to the global map
				w.Register()
			}
		}
	}
	//we need two steps to add allocations as all the parent wallets must exists
	for _, a := range GlobalRefAllocations {
		wid := a.BelongsTo
		if wid != 0 {
			w := GetRefWalletFromId(wid)
			w.AddAllocation(a.Id)
		}
	}
	//fill usage
	for aId, usage := range RefAllocationLocalUsage {
		a := GetRefAllocationFromId(aId)
		w := GetRefWalletFromId(a.BelongsTo)
		w.LocalUsage += usage
		addRefAllocationLocalUsage(aId, usage)
	}
	//check usage
	for wId, w := range GlobalRefWallets {
		usageFromFile := RefWalletTotalUsage[wId]
		usage := w.TotalUsage()
		diff := usageFromFile - usage
		if diff < 0 {
			diff = -diff
		}
		if diff > 10 {
			fmt.Printf("Warning w%d usage mismatch: from file=%d computed=%d\n\n", wId, usageFromFile, usage)
		}
	}
}

func addRefAllocationLocalUsage(id int64, amount int64) {
	//TODO: we assume all allocations are not retired
	if id == 0 || amount == 0 {
		return
	}
	//increase allocation group tree usage
	a := GetRefAllocationFromId(id)
	w := GetRefWalletFromId(a.BelongsTo)
	ag := w.AllocationsByParent[a.ParentWallet]
	ag.treeUsage += amount
	w.AllocationsByParent[a.ParentWallet] = ag

	//increase parent child usage
	if a.ParentWallet != 0 {
		p := GetRefWalletFromId(a.ParentWallet)
		p.ChildrenUsage[a.BelongsTo] += amount
	}

	//go up the allocation chain
	next := RefAllocationParentAllocation[id]
	addRefAllocationLocalUsage(next, amount)
}

func CreateRefWalletHierarchy(allocations []RefAllocation, isCapacityBased bool) {
	fillRefGlobalAllocations(allocations)
	fillRefGlobalWallets(isCapacityBased)
}

func refReadFile(path string) (lines []string) {
	readFile, err := os.Open(path)

	if err != nil {
		panic(err)
	}

	fileScanner := bufio.NewScanner(readFile)
	fileScanner.Split(bufio.ScanLines)
	for fileScanner.Scan() {
		lines = append(lines, fileScanner.Text())
	}

	readFile.Close()
	return lines
}

func refStringIsEmpty(str string) bool {
	var empty bool = true
	for i := 0; i < len(str); i++ {
		if str[i] != ' ' {
			empty = false
			break
		}
	}
	return empty
}

// Creates allocations from files with format id,associatedWallet,ParentWallet,allocationTotalUsage,quota,starttime(millisec),endtime(millisec),allocationLocalUsage, allocation_path
func CreateRefWalletHierarchyFromFile(path string, isCapacityBased bool) {
	lines := refReadFile(path)
	GlobalRefAllocations = make(map[int64]*RefAllocation, len(lines))
	RefAllocationLocalUsage = make(map[int64]int64, len(lines))
	RefWalletTotalUsage = make(map[int64]int64, len(lines))
	RefAllocationParentAllocation = make(map[int64]int64, len(lines))
	for _, line := range lines {
		segments := strings.Split(line, ",")

		id, _ := strconv.ParseInt(segments[0], 10, 64)

		associatedWallet, _ := strconv.ParseInt(segments[1], 10, 64)

		parentWallet := int64(0)
		if !refStringIsEmpty(segments[2]) {
			parentWallet, _ = strconv.ParseInt(segments[2], 10, 64)
		}

		totusage, _ := strconv.ParseInt(segments[3], 10, 64)
		RefWalletTotalUsage[associatedWallet] += totusage

		quota, _ := strconv.ParseInt(segments[4], 10, 64)

		start, _ := strconv.ParseInt(segments[5], 10, 64)
		startTime := time.UnixMilli(start)

		endTime := time.UnixMilli(int64(4891363200000))
		if !refStringIsEmpty(segments[6]) {
			end, _ := strconv.ParseInt(segments[6], 10, 64)
			endTime = time.UnixMilli(end)
		}

		if len(segments) > 7 && !refStringIsEmpty(segments[7]) {
			locusage, _ := strconv.ParseInt(segments[7], 10, 64)
			if locusage > 0 {
				RefAllocationLocalUsage[id] = locusage
			}
			if totusage < locusage {
				fmt.Printf("Warning A%d: totUsage < locUsage\n", id)
			}
		}

		if len(segments) > 8 && !refStringIsEmpty(segments[8]) {
			pStrings := strings.Split(segments[8], ".")
			parentAllocs := make([]int64, len(pStrings))
			for i, s := range strings.Split(segments[8], ".") {
				index, _ := strconv.ParseInt(s, 10, 64)
				parentAllocs[len(pStrings)-1-i] = index
			}
			current := id
			for _, aId := range parentAllocs {
				id, ok := RefAllocationParentAllocation[current]
				if ok && id != aId {
					log.Fatalf("Inconsistent allocation paths")
				} else {
					RefAllocationParentAllocation[current] = aId
				}
				current = aId
			}
		}

		allocation := RefAllocation{
			Id:           id,
			BelongsTo:    associatedWallet,
			ParentWallet: parentWallet,
			Quota:        quota,
			Start:        startTime,
			End:          endTime,
			Retired:      false,
			RetiredUsage: 0,
		}

		GlobalRefAllocations[allocation.Id] = &allocation
	}
	fillRefGlobalWallets(isCapacityBased)
}

func DestroyRefWalletHierarchy() {
	GlobalRefAllocations = nil
	GlobalRefWallets = nil
}

var ComplexRefWallets = []RefAllocation{
	{ //1
		BelongsTo:    1,
		ParentWallet: 0,
		Quota:        2500,
	},
	{ //2
		BelongsTo:    2,
		ParentWallet: 1,
		Quota:        600,
	},
	{ //3
		BelongsTo:    3,
		ParentWallet: 1,
		Quota:        10000,
	},
	{ //4
		BelongsTo:    4,
		ParentWallet: 2,
		Quota:        334,
	},
	{ //5
		BelongsTo:    4,
		ParentWallet: 2,
		Quota:        333,
	},
	{ //6
		BelongsTo:    4,
		ParentWallet: 2,
		Quota:        333,
	},
	{ //7
		BelongsTo:    5,
		ParentWallet: 4,
		Quota:        1000,
	},
	{ //8
		BelongsTo:    5,
		ParentWallet: 2,
		Quota:        1000,
	},
	{ //9
		BelongsTo:    5,
		ParentWallet: 3,
		Quota:        2000,
	},
	{ //10
		BelongsTo:    6,
		ParentWallet: 0,
		Quota:        2500,
	},
	{ //11
		BelongsTo:    3,
		ParentWallet: 6,
		Quota:        2000,
	},
	{ //12
		BelongsTo:    5,
		ParentWallet: 6,
		Quota:        1000,
	},
	{ //13
		BelongsTo:    2,
		ParentWallet: 1,
		Quota:        600,
	},
}

var SimpleRefWallets = []RefAllocation{
	{ //1
		BelongsTo:    1,
		ParentWallet: 0,
		Quota:        2500,
	},
	{ //2
		BelongsTo:    2,
		ParentWallet: 1,
		Quota:        600,
	},
	{ //3
		BelongsTo:    3,
		ParentWallet: 2,
		Quota:        300,
	},
}

// wallet/print.go
// =====================================================================================================================

// printing functions
func (a RefAllocation) String() string {
	return fmt.Sprintf("A%d {parent=W%d quota=%d}", a.Id, a.ParentWallet, a.Quota)
}

func (a RefAllocation) CompactString() string {
	return fmt.Sprintf("A%d {quota=%d}", a.Id, a.Quota)
}

//TODO: needs to be updated
// // printing function
// func (w Wallet) String() string {
// 	ts := time.Now()
// 	W := fmt.Sprintf("W%d {lU=%d} ", w.id, w.localUsage)
// 	A := ""
// 	if len(w.childrenUsage) > 0 {
// 		A += "{cU: "
// 		for id, u := range w.childrenUsage {
// 			A += fmt.Sprintf("W%d=%d ", id, u)
// 		}
// 		A += "} "
// 	}
// 	for wId, g := range w.allocationsByParent {
// 		if wId == 0 {
// 			A += "[ ROOT "
// 		} else {
// 			A += fmt.Sprintf("[ ->W%d ", wId)
// 		}
// 		A += fmt.Sprintf("tU=%d/%d", g.treeUsage, totalQuota(g.allocations, ts))
// 		for _, id := range g.allocations {
// 			a := getAllocationFromId(id)
// 			if !a.IsActive(ts) {
// 				continue
// 			}
// 			A += " " + a.CompactString()
// 		}
// 		A += " ]"
// 	}
// 	return W + A
// }

// refContains checks if a string is present in a slice
func refContains(l []int64, e int64) bool {
	for _, v := range l {
		if v == e {
			return true
		}
	}

	return false
}

// print a markdown/mermaid representation of the wallet hierarchy
func PrintRefWalletGraph(wallets []int64) {
	ts := time.Now()

	fmt.Printf("```mermaid\ngraph BT;\n")

	for _, wId := range wallets {
		w := GetRefWalletFromId(wId)
		if w == nil {
			fmt.Println("Invalid wallet id found")
			return
		}
		isRoot := w.IsRootWallet()
		if isRoot {
			fmt.Printf("subgraph root[\"Root wallets\"]\n")
		}
		fmt.Printf("subgraph W%d[W%d lU:%d lR:%d eU:%d tA:%d oA:%d ", w.Id, w.Id, w.LocalUsage, w.LocalRetiredUsage, w.ExcessUsage, w.TotalAllocated, w.Overallocation())
		A := ""
		if len(w.ChildrenUsage) > 0 || len(w.ChildrenRetiredUsage) > 0 {
			A += "cU: "
			for id, u := range w.ChildrenUsage {
				if !refContains(wallets, id) {
					continue
				}
				A += fmt.Sprintf("W%d=%d ", id, u)
			}
			for id, u := range w.ChildrenRetiredUsage {
				if !refContains(wallets, id) {
					continue
				}
				A += fmt.Sprintf("W%dR=%d ", id, u)
			}
			A += " "
		}
		fmt.Printf("%s ]\n", A)

		edges := ""
		for id, ag := range w.AllocationsByParent {
			// if !isRoot {
			fmt.Printf("subgraph W%dW%d[\"tU: %d tR: %d pB:%d\"]\n", w.Id, id, ag.treeUsage, ag.retiredTreeUsage, ag.PreferredBalance(ts))
			// }
			for a := range ag.allocations {
				allocString := fmt.Sprintf("[\"A%d r/q: %d/%d\"]", a, GetRefAllocationFromId(a).RetiredUsage, GetRefAllocationFromId(a).Quota)
				if GetRefAllocationFromId(a).Retired {
					allocString = "(" + allocString + ")"
				}
				fmt.Printf("A%d%s\n", a, allocString)
			}
			// a.allocations
			fmt.Println("end")
			if !isRoot {
				edges += fmt.Sprintf("W%dW%d-->W%d\n", w.Id, id, id)
			}
		}
		fmt.Println("end")
		if len(edges) > 0 {
			fmt.Print(edges)
		}
		if isRoot {
			fmt.Println("end")
		}
	}
	fmt.Println("```")
}

// wallet/utils.go
// =====================================================================================================================

// Generates a subgraph buttom-up from a wallet using the globalwallets and a walletId
func GenerateRefLocalWallets(walletId int64) (localWallets []int64, err error) {
	localWallets = make([]int64, 0, 8)
	queue := make([]int64, 0, 8)
	queue = append(queue, walletId)
	localWallets = append(localWallets, walletId)

	for len(queue) != 0 {
		currentWallet := queue[0]
		queue = queue[1:]

		wallet := GetRefWalletFromId(currentWallet)
		if wallet == nil {
			errmsg := fmt.Sprintf("Wallet %d does not exists\n", currentWallet)
			err = errors.New(errmsg)
			return
		}

	next:
		for pId := range wallet.AllocationsByParent {
			if pId == 0 {
				continue
			}
			for _, q := range localWallets {
				if q == pId {
					break next
				}
			}
			queue = append(queue, pId)
			localWallets = append(localWallets, pId)
		}
	}

	return
}

// return the list of key in a map
func MapRefKeys[T any](m map[int64]T) []int64 {
	keys := []int64{}
	for k := range m {
		keys = append(keys, k)
	}
	return keys
}

// wallet/wallet.go
// =====================================================================================================================

type RefWallet struct {
	Id                    int64
	LocalUsage            int64                   // sums of direct bills which can be billed to our parents
	LocalRetiredUsage     int64                   // sum of locally retired allocations
	ExcessUsage           int64                   // children usage charged to the wallet which can't be covered the rest of the direct bills which can not be billed to our parents
	AllocationsByParent   map[int64]allocRefGroup // allocations grouped by parent wallet
	ChildrenUsage         map[int64]int64         // active children usage
	ChildrenRetiredUsage  map[int64]int64         // retired children usage
	TotalAllocated        int64                   // total of active allocations to our children
	TotalRetiredAllocated int64                   // total of retired allocations to our children
	IsCapacityBased       bool                    // is this wallet product time-based or capacity-based?
}

// Global data structure
var GlobalRefWallets map[int64]*RefWallet

func GetRefWalletFromId(id int64) *RefWallet {
	return GlobalRefWallets[id]
}

func NewRefWallet(wid int64, isCapacityBased bool) *RefWallet {
	//create new wallet
	return &RefWallet{
		Id: wid,
		// LocalUsage:           GlobalWalletUsage[wid],
		AllocationsByParent:  map[int64]allocRefGroup{},
		ChildrenUsage:        map[int64]int64{},
		ChildrenRetiredUsage: map[int64]int64{},
		IsCapacityBased:      isCapacityBased,
	}
}

func (w *RefWallet) Register() (err error) {
	_, ok := GlobalRefWallets[w.Id]

	if ok {
		errmsg := fmt.Sprintf("W%d: Refusing to register wallet with already existing id\n", w.Id)
		err = errors.New(errmsg)
		return
	}

	GlobalRefWallets[w.Id] = w

	return
}

// add an allocation to the current wallet
func (w *RefWallet) AddAllocation(id int64) (err error) {
	a := GetRefAllocationFromId(id)

	//check that allocation belongs to us
	if a.BelongsTo != w.Id {
		errmsg := fmt.Sprintf("W%d: refusing to add A%d belonging to another wallet W%d.", w.Id, a.Id, a.BelongsTo)
		err = errors.New(errmsg)
		return
	}

	//check that allocation is not retired
	if a.Retired {
		errmsg := fmt.Sprintf("W%d: Refusing to add retired A%d\n", w.Id, a.Id)
		err = errors.New(errmsg)
		return
	}

	//add allocation to the list by parent
	aGroup, ok := w.AllocationsByParent[a.ParentWallet]
	if !ok {
		aGroup = *NewRefAllocGroup()
	}

	if time.Now().Before(a.Start) {
		aGroup.allocations[id] = false
	} else {
		aGroup.allocations[id] = true
		//update earliest expiration date for the allocation group
		if aGroup.earliestExpiration == (time.Time{}) || aGroup.earliestExpiration.After(a.End) {
			aGroup.earliestExpiration = a.End
		}
	}

	w.AllocationsByParent[a.ParentWallet] = aGroup

	//update parent wallet TotalAllocated
	if a.ParentWallet != 0 && aGroup.allocations[id] {
		GetRefWalletFromId(a.ParentWallet).TotalAllocated += a.Quota
	}

	//rebalance excess usage
	if w.ExcessUsage > 0 {
		ts := time.Now()
		amount := w.ExcessUsage
		w.LocalUsage -= amount
		newAmount, changed := w.internalCharge(amount, ts)
		//update local usage and overspending
		//check if we are overspending this wallet and keep track of expense
		w.LocalUsage += amount
		w.ExcessUsage = amount - newAmount
		checkRefErrors(changed)
	}

	return
}

// TODO: remove this
// this is for testing only
func (w *RefWallet) SetActive(allocId int64, state bool) {
	p := GetRefAllocationFromId(allocId).ParentWallet
	w.AllocationsByParent[p].allocations[allocId] = state
}

////////////////////////

// sums all active quotas in the wallet
func (w *RefWallet) totalActiveQuota() (res int64) {
	for _, ag := range w.AllocationsByParent {
		res += ag.totalActiveQuota()
	}
	return res
}

// sums all tree usage in the wallet
func (w *RefWallet) totalTreeUsage() (res int64) {
	for _, ag := range w.AllocationsByParent {
		res += ag.treeUsage
	}
	return res
}

// this check if wallet id 0 is a parent
func (w *RefWallet) IsRootWallet() bool {
	//TODO: we have root wallet with other parents
	//Note that this is not correct is some allocations have another parent but some don't
	//we should likely disallow this case
	_, isRoot := w.AllocationsByParent[0]
	return isRoot
}

// this check if wallet id 0 is a parent
func (w *RefWallet) IsOverspending() bool {
	if w.ExcessUsage > 0 {
		return true
	}
	//TODO: I dont think this is needed anymore
	if w.TotalUsage() > w.totalActiveQuota() {
		return true
	}

	return false
}

// total usage of the wallet is: tU := lU + sum cU + sum cR
// tU: total Usage
// lU: local Usage
// cU: children active usage
// cR: children retired usage
func (w *RefWallet) TotalUsage() (totUsage int64) {
	totUsage = w.LocalUsage
	for _, u := range w.ChildrenUsage {
		totUsage += u
	}
	if !w.IsCapacityBased {
		for _, u := range w.ChildrenRetiredUsage {
			totUsage += u
		}
	}
	return
}

func (w *RefWallet) childUsage(childId int64) (totUsage int64) {
	//TODO: check if childId is our child
	totUsage = w.ChildrenUsage[childId]
	if !w.IsCapacityBased {
		totUsage += w.ChildrenRetiredUsage[childId]
	}
	return
}

func (w *RefWallet) treeUsage(parentId int64) (totUsage int64) {
	//TODO: check if childId is our child
	totUsage = w.AllocationsByParent[parentId].treeUsage
	if !w.IsCapacityBased {
		totUsage += w.AllocationsByParent[parentId].retiredTreeUsage
	}
	return
}

func (w *RefWallet) Overallocation() (totUsage int64) {
	totUsage = w.TotalAllocated + w.TotalRetiredAllocated + w.LocalUsage - w.ExcessUsage - w.totalActiveQuota()
	if !w.IsCapacityBased {
		totUsage -= w.LocalRetiredUsage
	}
	return
}

func (w *RefWallet) parentEdgeCost(pId int64, ts time.Time) (cost int64) {
	ag := w.AllocationsByParent[pId]

	//TODO:
	//* test if the factors below work well in practice
	//* move the weight(s) for the factors into the product category description
	preferredBalance := int64(0)
	if w.IsCapacityBased {
		preferredBalance = ag.totalActiveQuota()
	} else {
		preferredBalance = ag.PreferredBalance(ts)
	}

	// balance
	const balanceWeight = int64(1 << 25)
	balanceFactor := ag.treeUsage - preferredBalance
	balanceFactor *= balanceWeight
	//time
	const timeWeight = int64(1 << 0)
	timeFactor := int64(0)
	timeToExpire := ag.earliestExpiration.Unix() - ts.Unix()
	timeFactor = timeWeight * timeToExpire

	cost = balanceFactor + timeFactor
	return
}

func (w *RefWallet) BuildGraph(ts time.Time, withOverallocation bool) (g *RefGraph) {
	//this part can be precomputed per wallet
	//and updated every time allocations are added or expire
	IndexInv := make(map[int64]int)
	queue := make([]int64, 0, 8)
	queue = append(queue, w.Id)
	index := make([]int64, 0, 8)
	IndexInv[w.Id] = 0
	index = append(index, w.Id)
	//add root wallet
	IndexInv[0] = 1
	index = append(index, 0)
	for len(queue) != 0 {
		wId := queue[0]
		queue = queue[1:]
		for pId, ag := range GetRefWalletFromId(wId).AllocationsByParent {
			_, ok := IndexInv[pId]
			if !ok && ag.IsActive() {
				if pId != 0 {
					queue = append(queue, pId)
				}
				IndexInv[pId] = len(index)
				index = append(index, pId)
			}
		}
	}

	//now we build a graph for the utilization flow
	gSize := len(IndexInv)
	graphSize := gSize
	if withOverallocation {
		graphSize = 2 * gSize
	}
	g = NewRefGraph(graphSize)

	//add index to graph
	g.Index = index
	g.IndexInv = IndexInv

	rootIndex := g.IndexInv[0]

	//add the edges with their capacity and weighted with their cost
	for wId, gIndex := range g.IndexInv {
		if wId != 0 {
			wLocal := GetRefWalletFromId(wId)
			// g.AddMinFlow(gIndex, w.LocalRetiredUsage)
			for pId, ag := range wLocal.AllocationsByParent {
				if !ag.IsActive() {
					continue
				}
				capacity := ag.totalActiveQuota() - ag.treeUsage
				g.AddEdge(g.IndexInv[pId], gIndex, capacity, ag.treeUsage)

				cost := wLocal.parentEdgeCost(pId, ts)
				g.AddEdgeCost(g.IndexInv[pId], gIndex, cost)
			}
			if withOverallocation {
				//add overallocation edge
				overallocation := wLocal.TotalAllocated + wLocal.TotalRetiredAllocated + wLocal.LocalUsage - wLocal.totalActiveQuota()
				if !wLocal.IsCapacityBased {
					overallocation -= wLocal.LocalRetiredUsage
				}
				if overallocation > 0 {
					usage := wLocal.TotalUsage() - wLocal.totalTreeUsage()
					if !wLocal.IsCapacityBased {
						usage -= wLocal.LocalRetiredUsage
					}
					usage = max(0, usage)
					//TODO: usage must always be <= overallocation
					g.AddEdge(rootIndex, gSize+gIndex, overallocation-usage, usage)
					g.AddEdgeCost(rootIndex, gSize+gIndex, (math.MaxInt64 >> 4)) //TODO: this is a hack to make the priority of the overallocation link very small
					g.AddEdge(gSize+gIndex, gIndex, overallocation-usage, usage)
					g.AddEdgeCost(gSize+gIndex, gIndex, (math.MaxInt64 >> 4))
				}
			}
		}
	}

	return
}

// report max usable charge for wallet and in each individual parent walelt
func (w *RefWallet) MaxUsable(ts time.Time) (maxU int64, parentSplit map[int64]int64) {
	//build graph
	g := w.BuildGraph(ts, false)

	//Compute max flow from root to current wallet index 0
	rootIndex := g.IndexInv[0]
	maxU = g.MaxFlow(rootIndex, 0)

	// we also report the split of maxU among the parent wallets
	parentSplit = make(map[int64]int64, len(w.AllocationsByParent))
	for index, value := range g.Adj[0] {
		// for index := 1; index < (g.V / 2); index++ {
		// value := g.Adj[0][index]
		if value > 0 {
			parentSplit[g.Index[index]] = value - w.AllocationsByParent[g.Index[index]].treeUsage
			// flow := g.MaxPathFlow(index, rootIndex)
			// parentSplit[g.Index[index]] = min(value, flow)
		}
	}

	return
}

func (w *RefWallet) internalCharge(amount int64, ts time.Time) (maxU int64, changed []int64) {
	// //this part can be precomputed per wallet
	// //and updated every time allocations are added or expire
	//build graph
	g := w.BuildGraph(ts, true)

	//Compute max flow from root to current wallet index 0
	rootIndex := g.IndexInv[0]
	if amount < 0 {
		//for a negative amount we flow a positive amount in the opposite direction
		maxU = g.MinCostFlow(0, rootIndex, -amount, true)
	} else {
		maxU = g.MinCostFlow(rootIndex, 0, amount, false)
	}

	//we keep track of wallets that are changed for error reporting
	//right now we simply add all the wallets in the hierarchy
	changed = make([]int64, 0, 8)

	if maxU != 0 {
		//process charges from graph
		gSize := g.V / 2
		for senderIndex := 0; senderIndex < gSize; senderIndex++ {
			sndId := g.Index[senderIndex]
			sndW := GetRefWalletFromId(sndId)
			if sndW != nil {
				changed = append(changed, sndId)
				sndW.ExcessUsage = g.Adj[senderIndex][senderIndex+gSize]
			}
			for receiverIndex := 0; receiverIndex < gSize; receiverIndex++ {
				amount := g.Adj[senderIndex][receiverIndex]
				if g.Original[receiverIndex][senderIndex] {
					//process charge
					rcvId := g.Index[receiverIndex]
					ag := sndW.AllocationsByParent[rcvId]
					ag.treeUsage = amount
					sndW.AllocationsByParent[rcvId] = ag
					if rcvId != 0 {
						pW := GetRefWalletFromId(rcvId)
						pW.ChildrenUsage[sndId] = amount
					}
				}
			}
		}
	}

	return
}

func checkRefErrors(changed []int64) (errmsg string) {
	for _, wId := range changed {
		w := GetRefWalletFromId(wId)
		if w.IsOverspending() {
			errmsg += fmt.Sprintf(" W%d not enough credits.", w.Id)
		}
	}
	return
}

func (w *RefWallet) charge(c *RefProductCharge) (e error) {
	errmsg := ""
	defer func() {
		if errmsg != "" {
			e = errors.New(errmsg)
		}
	}()

	//check if charge belongs to us
	if w.Id != c.WalletId {
		errmsg += fmt.Sprintf("W%d: refusing to process charge for another wallet W%d.", w.Id, c.WalletId)
		return
	}

	//check if we are negatively charging more than the current local usage
	if c.Amount+w.LocalUsage < 0 {
		errmsg += fmt.Sprintf("W%d: refusing to process negative charge (%d) exceeding local wallet usage (lU:%d).", w.Id, c.Amount, w.LocalUsage)
		return
	}

	if c.Amount == 0 {
		//nothing to do
		return
	}

	totCharged, changed := w.internalCharge(c.Amount, c.Ts)

	//update local usage and overspending
	//check if we are overspending this wallet and keep track of expense
	if c.Amount > 0 {
		w.LocalUsage += c.Amount
		w.ExcessUsage += c.Amount - totCharged
	} else {
		w.LocalUsage -= totCharged
	}

	//process errors
	errmsg += checkRefErrors(changed)

	return
}

func (w *RefWallet) Retire(a *RefAllocation) (e error) {
	//Retirement of an allocation commits some of the usage of the wallet (not allocation group) permanently to allocation

	//check if the allocation belong to us
	if w.Id != a.BelongsTo {
		msg := fmt.Sprintf("W%d: trying to retire A%d but it belongs to W%d", w.Id, a.Id, a.BelongsTo)
		e = errors.New(msg)
		return
	}

	//check if the allocation has any quota
	if a.Quota == 0 {
		//is this case even allowed? yes we have many such allocations around
		//there is nothing to do just retire the allocation
		a.Retired = true
		return
	}

	//move alloc group usage to the retired allocation up to its quota
	ag, ok := w.AllocationsByParent[a.ParentWallet]

	//check if the allocation was added this wallet
	if ok {
		isValid, ok := ag.allocations[a.Id]
		if ok && !isValid {
			msg := fmt.Sprintf("W%d: trying to retire A%d but allocation is not active", w.Id, a.Id)
			e = errors.New(msg)
			return
		}
	}
	//this is NOT an else because the value of ok get changed inside the previous if
	if !ok {
		msg := fmt.Sprintf("W%d: trying to retire A%d but it was never added to the wallet", w.Id, a.Id)
		e = errors.New(msg)
		return
	}

	toRetire := min(a.Quota, ag.treeUsage)

	// store retired usage in allocation
	a.RetiredUsage = toRetire
	a.Retired = true

	//update wallet local retired usage
	w.LocalRetiredUsage += toRetire

	//update allocation group retired tree usage
	ag.retiredTreeUsage += toRetire
	ag.treeUsage -= toRetire
	ag.allocations[a.Id] = false
	w.AllocationsByParent[a.ParentWallet] = ag

	//update parent wallet child retired usage
	var pW *RefWallet
	if a.ParentWallet != 0 {
		pW = GetRefWalletFromId(a.ParentWallet)
		pW.ChildrenUsage[w.Id] -= toRetire
		pW.ChildrenRetiredUsage[w.Id] += toRetire
		pW.TotalAllocated -= a.Quota
		pW.TotalRetiredAllocated += a.Quota
	}

	if toRetire == 0 {
		//nothing else to do
		return
	}

	//rebalance wallets if capacity based
	if w.IsCapacityBased {
		ts := time.Now()
		//rebalance parent wallet
		if pW != nil {
			pW.LocalUsage += toRetire
			pW.charge(&RefProductCharge{WalletId: pW.Id, Amount: -toRetire, Ts: ts})
		}
		//rebalance current wallet
		w.LocalUsage -= toRetire
		w.charge(&RefProductCharge{WalletId: w.Id, Amount: toRetire, Ts: ts})
	}

	return
}

// wallet/walletoperations.go
// =====================================================================================================================

// Charge wallet
type RefProductCharge struct {
	WalletId int64
	Amount   int64
	Ts       time.Time
}

// this is creating a new local charge
// it is meant to be the entrypoint in the wallet charging system
func (pc *RefProductCharge) Process() error {
	return GetRefWalletFromId(pc.WalletId).charge(pc)
}

func (pc *RefProductCharge) String() (out string) {
	out = fmt.Sprintf("[ts=%d] Charge W%d amount=%d", pc.Ts.UnixMilli(), pc.WalletId, pc.Amount)
	return
}

// retire allocation
type RefRetireAllocation struct {
	AllocationId int64
	Ts           time.Time
}

func (ra *RefRetireAllocation) Process() error {
	return GetRefAllocationFromId(ra.AllocationId).Retire()
}

func (ra *RefRetireAllocation) String() (out string) {
	a := GetRefAllocationFromId(ra.AllocationId)
	out = fmt.Sprintf("[ts=%d] Retired A%d from W%d rU=%d", ra.Ts.UnixMilli(), a.Id, a.BelongsTo, a.RetiredUsage)
	return
}

// add allocation
type RefAddAllocation struct {
	AllocationId int64
	Ts           time.Time
}

func (aa *RefAddAllocation) Process() error {
	wId := GetRefAllocationFromId(aa.AllocationId).BelongsTo
	return GetRefWalletFromId(wId).AddAllocation(aa.AllocationId)
}

func (aa *RefAddAllocation) String() (out string) {
	a := GetRefAllocationFromId(aa.AllocationId)
	out = fmt.Sprintf("[ts=%d] Added A%d to W%d quota=%d", aa.Ts.UnixMilli(), a.Id, a.BelongsTo, a.Quota)
	return
}

// set wallet amount
type RefSetWalletAmount struct {
	WalletId int64
	Amount   int64
	Ts       time.Time
}

func (swa *RefSetWalletAmount) Process() error {
	w := GetRefWalletFromId(swa.WalletId)
	currentTotal := w.TotalUsage()
	reqCharge := swa.Amount - currentTotal
	pCharge := RefProductCharge{
		WalletId: swa.WalletId,
		Amount:   reqCharge,
		Ts:       swa.Ts,
	}
	return pCharge.Process()
}

func (swa *RefSetWalletAmount) String() (out string) {
	out = fmt.Sprintf("[ts=%d] Set W%d to amount=%d", swa.Ts.UnixMilli(), swa.WalletId, swa.Amount)
	return
}
