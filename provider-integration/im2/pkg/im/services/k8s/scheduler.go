package k8s

import (
	"slices"
	"strings"
	"time"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type SchedulerFlag int

const (
	// SchedulerBackfill turns backfill scheduling on/off
	SchedulerBackfill SchedulerFlag = 1 << iota

	// SchedulerFavorSmallJobs gives a higher priority to small jobs (JobSizeFactor)
	SchedulerFavorSmallJobs

	// SchedulerAllowExtension turns the extension endpoint on or off
	SchedulerAllowExtension

	// SchedulerJobCompaction attempts to keep the jobs on as few nodes as possible
	SchedulerJobCompaction
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

	// As a percentage of the active cluster (0.0 ... 1.0). The effective maximum job size will never be less than
	// two full nodes, where a full node is determined by the largest node in the set of active nodes. Note that it
	// is also not be possible to submit a job which requests more resources that are available in the cluster (even
	// if that is less than two full nodes).
	MaximumJobSize float64

	MaximumTimeAllocation util.Option[time.Duration]

	// A percentage (0.01 ... 1.0) which describes how much of the resources are guaranteed and how much is burstable.
	// A value of 1 will guarantee 100% of the resources disabling bursting. A value of 0.01 will guarantee only 1%
	// of the resources allowing the remaining 99% to be burstable resources. It is not possible to guarantee less than
	// 1% of the resource request.
	GuaranteedResourceFraction float64
}

func NewScheduler() *Scheduler {
	return &Scheduler{
		Nodes:    make(map[string]*SchedulerNode),
		Projects: make(map[string]*SchedulerProject),
	}
}

type SchedulerNode struct {
	Name     string
	Category string
	SchedulerDimensions
	Initial         SchedulerDimensions
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

func (dims *SchedulerDimensions) Satisfies(request SchedulerDimensions) bool {
	return dims.CpuMillis >= request.CpuMillis && dims.Gpu >= request.Gpu && dims.MemoryInBytes >= request.MemoryInBytes
}

type SchedulerProject struct {
	Usage     uint64  // treeUsage
	Quota     uint64  // maxUsable + treeUsage (i.e. not the actual allocation quota)
	FairShare float64 // most recently computed fair share value (0.0 ... 1.0)
}

type SchedulerQueueEntry struct {
	JobId    string
	Category string
	SchedulerDimensions
	Replicas       int
	LastSeen       int
	Priority       float64
	Data           any
	OffensiveScore int
	DefensiveScore int
}

type SchedulerReplicaEntry struct {
	JobId string
	Rank  int
	SchedulerDimensions
	Node           string
	LastSeen       int
	Data           any
	DefensiveScore int // inherited from the queue entry
}

func (s *Scheduler) RegisterNode(name string, category string, cpuMillis int, memoryInBytes int, gpus int, unschedulable bool) {
	existing, ok := s.Nodes[name]
	if ok {
		existing.LastSeen = s.Time
		existing.Unschedulable = unschedulable
		existing.Category = category
		return
	}

	s.Nodes[name] = &SchedulerNode{
		Name:     name,
		Category: category,
		SchedulerDimensions: SchedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: memoryInBytes,
			Gpu:           gpus,
		},
		Initial: SchedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: memoryInBytes,
			Gpu:           gpus,
		},
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
		log.Warn("Registering a running replica %d %d on %s but node is not known to the scheduler", jobId, rank, node)
		return
	}

	s.Replicas = append(s.Replicas, SchedulerReplicaEntry{
		JobId:               jobId,
		Rank:                rank,
		SchedulerDimensions: dimensions,
		Node:                node,
		LastSeen:            s.Time,
		Data:                data,
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
			dims := &node.SchedulerDimensions
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
	category string,
	dimensions SchedulerDimensions,
	priority float64,
	replicas int,
	data any,
) {
	s.Queue = append(s.Queue, SchedulerQueueEntry{
		JobId:               jobId,
		Category:            category,
		SchedulerDimensions: dimensions,
		Replicas:            replicas,
		LastSeen:            s.Time,
		Data:                data,
		Priority:            priority,
	})
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

	if queueLength == 0 {
		return nil
	}

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

	var allNodes []*SchedulerNode
	for _, node := range s.Nodes {
		allNodes = append(allNodes, node)
	}

	for queueIdx := 0; queueIdx < len(s.Queue); queueIdx++ {
		entry := &s.Queue[queueIdx]
		allocatedNodes := s.trySchedule(entry, allNodes)

		if allocatedNodes != nil {
			for rank := 0; rank < len(allocatedNodes); rank++ {
				replicaEntry := SchedulerReplicaEntry{
					JobId:               entry.JobId,
					Rank:                rank,
					SchedulerDimensions: entry.SchedulerDimensions,
					Node:                allocatedNodes[rank],
					LastSeen:            now,
					Data:                entry.Data,
				}

				result = append(result, replicaEntry)
				s.Replicas = append(s.Replicas, replicaEntry)
			}

			s.Queue = append(s.Queue[:queueIdx], s.Queue[queueIdx+1:]...)
			queueIdx--
		}
	}

	s.Time++
	return result
}

func (s *Scheduler) trySchedule(entry *SchedulerQueueEntry, allNodes []*SchedulerNode) []string {
	var allocatedNodes []string

	slices.SortFunc(allNodes, func(a, b *SchedulerNode) int {
		cmp := numericCompares(a.Gpu, b.Gpu, a.CpuMillis, b.CpuMillis, a.MemoryInBytes, b.MemoryInBytes)
		if cmp != 0 {
			return cmp
		}

		return strings.Compare(a.Name, b.Name)
	})

	// NOTE(Dan): The outer loop is needed since it is possible to allocate multiple replicas to the same node.
outer:
	for len(allocatedNodes) < entry.Replicas {
		for _, node := range allNodes {
			if node.Category != entry.Category {
				continue
			}

			if node.Unschedulable {
				continue
			}

			if !node.Satisfies(entry.SchedulerDimensions) {
				continue
			}

			node.Subtract(entry.SchedulerDimensions)
			allocatedNodes = append(allocatedNodes, node.Name)
			continue outer
		}

		// NOTE(Dan): If we reach this point, we have tried all nodes and will not be able to succeed.
		// Break and fail soon after in the normal branch.
		break
	}

	if len(allocatedNodes) < entry.Replicas {
		for _, n := range allocatedNodes {
			s.Nodes[n].Add(entry.SchedulerDimensions)
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
