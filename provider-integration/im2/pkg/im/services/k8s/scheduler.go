package k8s

import (
	"math"
	"slices"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
)

type SchedulerFlag int

const (
	SchedulerDisableBackfill SchedulerFlag = 1 << iota
	SchedulerFavorLargeJobs
	SchedulerDisableJobCompaction
)

type Scheduler struct {
	Queue           []SchedulerQueueEntry
	Replicas        []SchedulerReplicaEntry
	Nodes           map[string]*SchedulerNode
	Projects        map[string]*SchedulerProject
	Time            int
	WeightAge       float64
	WeightFairShare float64
	WeightJobSize   float64

	Flags SchedulerFlag

	// As a percentage of the active cluster (0.0 ... 1.0). The effective maximum job size will never be less than
	// two full nodes, where a full node is determined by the largest node in the set of active nodes. Note that it
	// is also not be possible to submit a job which requests more resources that are available in the cluster (even
	// if that is less than two full nodes).
	//MaximumJobSize float64

	//MaximumTimeAllocation util.Option[time.Duration]

	// A percentage (0.01 ... 1.0) which describes how much of the resources are guaranteed and how much is burstable.
	// A value of 1 will guarantee 100% of the resources disabling bursting. A value of 0.01 will guarantee only 1%
	// of the resources allowing the remaining 99% to be burstable resources. It is not possible to guarantee less than
	// 1% of the resource request.
	//GuaranteedResourceFraction float64
}

func NewScheduler() *Scheduler {
	return &Scheduler{
		Nodes:           make(map[string]*SchedulerNode),
		Projects:        make(map[string]*SchedulerProject),
		WeightAge:       1,
		WeightFairShare: 1,
		WeightJobSize:   1,
	}
}

type SchedulerNode struct {
	Name            string
	Remaining       SchedulerDimensions // Remaining dimensions before hitting the Capacity (not the Limits!)
	Capacity        SchedulerDimensions // Capacity describes the dimensions of the node when it is not experiencing any faults
	Limits          SchedulerDimensions // Limits describe the largest usage the node is currently able to tolerate
	LastSeen        int
	Unschedulable   bool
	MaximumReplicas int // maximum number of replicas running on the node (regardless of reservations)
}

type SchedulerDimensions struct {
	CpuMillis     int
	MemoryInBytes int
	Gpu           int
}

func (dims *SchedulerDimensions) Add(other SchedulerDimensions) {
	dims.CpuMillis += other.CpuMillis
	dims.MemoryInBytes += other.MemoryInBytes
	dims.Gpu += other.Gpu
}

func (dims *SchedulerDimensions) Subtract(other SchedulerDimensions) {
	dims.CpuMillis -= other.CpuMillis
	dims.MemoryInBytes -= other.MemoryInBytes
	dims.Gpu -= other.Gpu
}

func (dims *SchedulerDimensions) AddImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{}
	result.CpuMillis = dims.CpuMillis + other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes + other.MemoryInBytes
	result.Gpu = dims.Gpu + other.Gpu
	return result
}

func (dims *SchedulerDimensions) SubtractImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{}
	result.CpuMillis = dims.CpuMillis - other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes - other.MemoryInBytes
	result.Gpu = dims.Gpu - other.Gpu
	return result
}

func (dims *SchedulerDimensions) Satisfies(request SchedulerDimensions) bool {
	return dims.CpuMillis >= request.CpuMillis && dims.Gpu >= request.Gpu && dims.MemoryInBytes >= request.MemoryInBytes
}

type SchedulerProject struct {
	Usage uint64 // treeUsage
	Quota uint64 // maxUsable + treeUsage (i.e. not the actual allocation quota)
}

type SchedulerQueueEntry struct {
	JobId string
	SchedulerDimensions
	Replicas    int
	LastSeen    int
	Data        any
	SubmittedAt fnd.Timestamp
	JobLength   orc.SimpleDuration
	Priority    float64
	Factors     struct {
		Age       float64
		FairShare float64
		JobSize   float64
	}
	//OffensiveScore int
	//DefensiveScore int
}

type SchedulerReplicaEntry struct {
	JobId string
	Rank  int
	SchedulerDimensions
	Node      string
	LastSeen  int
	Data      any
	JobLength orc.SimpleDuration
	//DefensiveScore int // inherited from the queue entry
}

func (s *Scheduler) RegisterNode(name string, capacity, limits SchedulerDimensions, unschedulable bool) {
	existing, ok := s.Nodes[name]
	if ok {
		existing.LastSeen = s.Time
		existing.Unschedulable = unschedulable
		existing.Limits = limits
		return
	}

	s.Nodes[name] = &SchedulerNode{
		Name:          name,
		Remaining:     capacity,
		Capacity:      capacity,
		Limits:        limits,
		LastSeen:      s.Time,
		Unschedulable: unschedulable,
	}
}

func (s *Scheduler) PruneNodes() []string {
	var result []string

	now := s.Time

	for _, node := range s.Nodes {
		if node.LastSeen != now {
			result = append(result, node.Name)
			delete(s.Nodes, node.Name)
		}
	}

	return result
}

func (s *Scheduler) RegisterRunningReplica(
	jobId string,
	rank int,
	dimensions SchedulerDimensions,
	node string,
	data any,
	jobLength orc.SimpleDuration,
) {
	now := s.Time
	length := len(s.Replicas)
	for i := 0; i < length; i++ {
		replica := &s.Replicas[i]
		if replica.JobId == jobId && replica.Rank == rank {
			replica.LastSeen = now
			return
		}
	}

	nodePtr := s.Nodes[node]
	if nodePtr == nil {
		log.Warn("Registering a running replica %v %d on %s but node is not known to the scheduler", jobId, rank, node)
		return
	}

	s.Replicas = append(s.Replicas, SchedulerReplicaEntry{
		JobId:               jobId,
		Rank:                rank,
		SchedulerDimensions: dimensions,
		Node:                node,
		LastSeen:            s.Time,
		Data:                data,
		JobLength:           jobLength,
	})
}

func (s *Scheduler) PruneReplicas() []SchedulerReplicaEntry {
	var result []SchedulerReplicaEntry

	now := s.Time

	for i := 0; i < len(s.Replicas); i++ {
		replica := &s.Replicas[i]
		if replica.LastSeen == now {
			continue
		}

		// NOTE(Dan): The node can be nil if the replica lost its node
		node := s.Nodes[replica.Node]
		if node != nil {
			dims := &node.Remaining
			dims.Add(replica.SchedulerDimensions)
		}

		result = append(result, *replica)

		lastIdx := len(s.Replicas) - 1
		if i != lastIdx {
			s.Replicas[i] = s.Replicas[lastIdx]
			i--
		}
		s.Replicas = s.Replicas[:lastIdx]
	}

	return result
}

func (s *Scheduler) RegisterJobInQueue(
	jobId string,
	dimensions SchedulerDimensions,
	replicas int,
	data any,
	submittedAt fnd.Timestamp,
	jobLength orc.SimpleDuration,
) {
	s.Queue = append(s.Queue, SchedulerQueueEntry{
		JobId:               jobId,
		SchedulerDimensions: dimensions,
		Replicas:            replicas,
		LastSeen:            s.Time,
		Data:                data,
		SubmittedAt:         submittedAt,
		JobLength:           jobLength,
	})
}

func (s *Scheduler) UpdateTimeAllocation(jobId string, newJobLength orc.SimpleDuration) {
	length := len(s.Queue)
	for i := 0; i < length; i++ {
		entry := &s.Queue[i]
		if entry.JobId == jobId {
			entry.JobLength = newJobLength
			return
		}
	}

	length = len(s.Replicas)
	for i := 0; i < length; i++ {
		entry := &s.Replicas[i]
		if entry.JobId == jobId {
			entry.JobLength = newJobLength
		}
	}
}

func (s *Scheduler) RemoveJobFromQueue(jobId string) bool {
	length := len(s.Queue)
	for i := 0; i < length; i++ {
		if s.Queue[i].JobId == jobId {
			if length > 1 {
				s.Queue[i] = s.Queue[length-1]
				i--
			}
			s.Queue = s.Queue[:length-1]
			return true
		}
	}
	return false
}

func (s *Scheduler) Schedule() []SchedulerReplicaEntry {
	var result []SchedulerReplicaEntry

	now := s.Time
	queueLength := len(s.Queue)

	if queueLength > 1 {
		// Calculate factors and set priority
		// -----------------------------------------------------------------------------------------------------------------
		{
			nowWallTime := time.Now()
			minWallTimeInQueue := math.MaxFloat64
			maxWallTimeInQueue := 0.0

			minCpu := math.MaxInt
			maxCpu := 0

			for i := 0; i < queueLength; i++ {
				entry := &s.Queue[i]

				submittedAtWall := time.Time(entry.SubmittedAt)
				secondsInQueue := nowWallTime.Sub(submittedAtWall).Seconds()
				cpusUsed := entry.SchedulerDimensions.CpuMillis * entry.Replicas

				if secondsInQueue < minWallTimeInQueue {
					minWallTimeInQueue = secondsInQueue
				}
				if secondsInQueue > maxWallTimeInQueue {
					maxWallTimeInQueue = secondsInQueue
				}

				if cpusUsed < minCpu {
					minCpu = cpusUsed
				}
				if cpusUsed > maxCpu {
					maxCpu = cpusUsed
				}
			}

			timeInQueueSpan := maxWallTimeInQueue - minWallTimeInQueue
			cpusUsedSpan := float64(maxCpu) - float64(minCpu)

			for i := 0; i < queueLength; i++ {
				entry := &s.Queue[i]

				submittedAtWall := time.Time(entry.SubmittedAt)
				secondsInQueue := nowWallTime.Sub(submittedAtWall).Seconds()
				cpusUsed := entry.SchedulerDimensions.CpuMillis * entry.Replicas

				if timeInQueueSpan == 0 {
					entry.Factors.Age = 1.0
				} else {
					entry.Factors.Age = (secondsInQueue - minWallTimeInQueue) / timeInQueueSpan
				}

				if cpusUsedSpan == 0 {
					entry.Factors.JobSize = 1.0
				} else {
					entry.Factors.JobSize = float64(cpusUsed-minCpu) / cpusUsedSpan

					if s.Flags&SchedulerFavorLargeJobs == 0 {
						entry.Factors.JobSize = 1 - entry.Factors.JobSize
					}
				}

				// TODO fairshare
				entry.Factors.FairShare = 1.0

				entry.Priority =
					s.WeightAge*entry.Factors.Age +
						s.WeightJobSize*entry.Factors.JobSize +
						s.WeightFairShare*entry.Factors.FairShare
			}
		}

		// Sort jobs according to priority
		// -----------------------------------------------------------------------------------------------------------------
		slices.SortFunc(s.Queue, func(a, b SchedulerQueueEntry) int {
			if a.Priority > b.Priority {
				return -1
			} else if b.Priority > a.Priority {
				return 1
			} else {
				if a.JobId < b.JobId {
					return -1
				} else if b.JobId > a.JobId {
					return 1
				} else {
					return 0
				}
			}
		})
	}

	var allNodes []*SchedulerNode
	for _, node := range s.Nodes {
		allNodes = append(allNodes, node)
	}

	allowBackfill := s.Flags&SchedulerDisableBackfill == 0
	for queueIdx := 0; queueIdx < len(s.Queue); queueIdx++ {
		entry := &s.Queue[queueIdx]
		allocatedNodes := s.trySchedule(entry, allNodes)

		// TODO Only backfill if we the job we are scheduling can likely complete before the head of the queue
		//   will start. This will require us to know when the head of the queue is likely to start.

		if allocatedNodes != nil {
			for rank := 0; rank < len(allocatedNodes); rank++ {
				replicaEntry := SchedulerReplicaEntry{
					JobId:               entry.JobId,
					Rank:                rank,
					SchedulerDimensions: entry.SchedulerDimensions,
					Node:                allocatedNodes[rank],
					LastSeen:            now,
					Data:                entry.Data,
					JobLength:           entry.JobLength,
				}

				result = append(result, replicaEntry)
				s.Replicas = append(s.Replicas, replicaEntry)
			}

			s.Queue = append(s.Queue[:queueIdx], s.Queue[queueIdx+1:]...)
			queueIdx--
		} else if !allowBackfill {
			break
		}
	}

	s.Time++
	return result
}

func (s *Scheduler) trySchedule(entry *SchedulerQueueEntry, allNodes []*SchedulerNode) []string {
	var allocatedNodes []string

	hasJobCompaction := s.Flags&SchedulerDisableJobCompaction == 0

	sortingFunction := func(a, b *SchedulerNode) int {
		aRem := a.Remaining
		bRem := b.Remaining
		cmp := numericCompares(aRem.Gpu, bRem.Gpu, aRem.CpuMillis, bRem.CpuMillis, aRem.MemoryInBytes, bRem.MemoryInBytes)
		if cmp != 0 {
			if !hasJobCompaction {
				// Use the least full node (which can carry the job)
				return cmp * -1
			} else {
				// Use the most full node (which can carry the job)
				return cmp
			}
		}

		return strings.Compare(a.Name, b.Name)
	}

	sortAfterEachSchedule := !hasJobCompaction

	if !sortAfterEachSchedule {
		slices.SortFunc(allNodes, sortingFunction)
	}

	// NOTE(Dan): The outer loop is needed since it is possible to allocate multiple replicas to the same node.
outer:
	for len(allocatedNodes) < entry.Replicas {
		if sortAfterEachSchedule {
			slices.SortFunc(allNodes, sortingFunction)
		}

		for _, node := range allNodes {
			if node.Unschedulable {
				continue
			}

			if !node.Remaining.Satisfies(entry.SchedulerDimensions) {
				continue
			}

			node.Remaining.Subtract(entry.SchedulerDimensions)

			// Check if the new usage on the node exceeds the limits of the node
			usage := node.Capacity.SubtractImmutable(node.Remaining)
			if !node.Limits.Satisfies(usage) {
				node.Remaining.Add(entry.SchedulerDimensions)
				continue
			}

			allocatedNodes = append(allocatedNodes, node.Name)
			continue outer
		}

		// NOTE(Dan): If we reach this point, we have tried all nodes and will not be able to succeed.
		// Break and fail soon after in the normal branch.
		break
	}

	if len(allocatedNodes) < entry.Replicas {
		for _, n := range allocatedNodes {
			s.Nodes[n].Remaining.Add(entry.SchedulerDimensions)
		}

		return nil
	}

	return allocatedNodes
}

func numericCompare(a, b int) int {
	if a < b {
		return -1
	} else if a > b {
		return 1
	}
	return 0
}

func numericCompares(numbers ...int) int {
	if len(numbers)%2 != 0 {
		log.Warn("numericCompares called incorrectly!")
		return 0
	}

	for i := 0; i < len(numbers); i += 2 {
		cmp := numericCompare(numbers[i], numbers[i+1])
		if cmp != 0 {
			return cmp
		}
	}
	return 0
}
