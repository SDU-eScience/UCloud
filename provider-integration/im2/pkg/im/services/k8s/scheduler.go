package k8s

import (
	"slices"
	"strings"
	"ucloud.dk/pkg/log"
)

type Scheduler struct {
	CategoryName              string
	PriorityPointsPerSchedule float64
	queue                     []SchedulerQueueEntry
	replicas                  []SchedulerReplicaEntry
	nodes                     map[string]*SchedulerNode
	time                      int
}

func NewScheduler(categoryName string, priorityPointsPerSchedule float64) *Scheduler {
	return &Scheduler{
		CategoryName:              categoryName,
		PriorityPointsPerSchedule: priorityPointsPerSchedule,
		nodes:                     make(map[string]*SchedulerNode),
	}
}

type SchedulerNode struct {
	Name string
	SchedulerDimensions
	Initial       SchedulerDimensions
	LastSeen      int
	Unschedulable bool
}

type SchedulerDimensions struct {
	Cpu    int
	Memory int
	Gpu    int
}

func (dims *SchedulerDimensions) Add(other SchedulerDimensions) {
	dims.Cpu += other.Cpu
	dims.Memory += other.Memory
	dims.Gpu += other.Gpu
}

func (dims *SchedulerDimensions) Subtract(other SchedulerDimensions) {
	dims.Cpu -= other.Cpu
	dims.Memory -= other.Memory
	dims.Gpu -= other.Gpu
}

func (dims *SchedulerDimensions) Satisfies(request SchedulerDimensions) bool {
	return dims.Cpu >= request.Cpu && dims.Gpu >= request.Gpu && dims.Memory >= request.Memory
}

type SchedulerQueueEntry struct {
	JobId int
	SchedulerDimensions
	Replicas int
	LastSeen int
	Priority float64
	Data     any
}

type SchedulerReplicaEntry struct {
	JobId int
	Rank  int
	SchedulerDimensions
	Node     string
	LastSeen int
	Data     any
}

func (s *Scheduler) RegisterNode(name string, cpuMillis int, memoryInBytes int, gpus int, unschedulable bool) {
	existing, ok := s.nodes[name]
	if ok {
		existing.LastSeen = s.time
		existing.Unschedulable = unschedulable
		return
	}

	s.nodes[name] = &SchedulerNode{
		Name: name,
		SchedulerDimensions: SchedulerDimensions{
			Cpu:    cpuMillis,
			Memory: memoryInBytes,
			Gpu:    gpus,
		},
		Initial: SchedulerDimensions{
			Cpu:    cpuMillis,
			Memory: memoryInBytes,
			Gpu:    gpus,
		},
		LastSeen:      s.time,
		Unschedulable: unschedulable,
	}
}

func (s *Scheduler) PruneNodes() []string {
	var result []string

	now := s.time

	for _, node := range s.nodes {
		if node.LastSeen != now {
			result = append(result, node.Name)
			delete(s.nodes, node.Name)
		}
	}

	return result
}

func (s *Scheduler) RegisterRunningReplica(
	jobId int,
	rank int,
	dimensions SchedulerDimensions,
	node string,
	data any,
) {
	now := s.time
	length := len(s.replicas)
	for i := 0; i < length; i++ {
		replica := &s.replicas[i]
		if replica.JobId == jobId && replica.Rank == rank {
			replica.LastSeen = now
			return
		}
	}

	nodePtr := s.nodes[node]
	if nodePtr == nil {
		log.Warn("Registering a running replica %d %d on %s but node is not known to the scheduler", jobId, rank, node)
		return
	}

	s.replicas = append(s.replicas, SchedulerReplicaEntry{
		JobId:               jobId,
		Rank:                rank,
		SchedulerDimensions: dimensions,
		Node:                node,
		LastSeen:            s.time,
		Data:                data,
	})
}

func (s *Scheduler) PruneReplicas() []SchedulerReplicaEntry {
	var result []SchedulerReplicaEntry

	now := s.time

	for i := 0; i < len(s.replicas); i++ {
		replica := &s.replicas[i]
		if replica.LastSeen == now {
			continue
		}

		// NOTE(Dan): The node can be nil if the replica lost its node
		node := s.nodes[replica.Node]
		if node != nil {
			dims := &node.SchedulerDimensions
			dims.Add(replica.SchedulerDimensions)
		}

		result = append(result, *replica)

		lastIdx := len(s.replicas) - 1
		if i != lastIdx {
			s.replicas[i] = s.replicas[lastIdx]
			i--
		}
		s.replicas = s.replicas[:lastIdx]
	}

	return result
}

func (s *Scheduler) RegisterJobInQueue(
	jobId int,
	dimensions SchedulerDimensions,
	priority float64,
	replicas int,
	data any,
) {
	s.queue = append(s.queue, SchedulerQueueEntry{
		JobId:               jobId,
		SchedulerDimensions: dimensions,
		Replicas:            replicas,
		LastSeen:            s.time,
		Data:                data,
		Priority:            priority,
	})
}

func (s *Scheduler) RemoveJobFromQueue(jobId int) bool {
	length := len(s.queue)
	for i := 0; i < length; i++ {
		if s.queue[i].JobId == jobId {
			if length > 1 {
				s.queue[i] = s.queue[length-1]
				i--
			}
			s.queue = s.queue[:length-1]
			return true
		}
	}
	return false
}

func (s *Scheduler) Schedule() []SchedulerReplicaEntry {
	var result []SchedulerReplicaEntry

	pointsPerSchedule := s.PriorityPointsPerSchedule
	now := s.time
	queueLength := len(s.queue)

	if queueLength == 0 {
		return nil
	}

	for i := 0; i < queueLength; i++ {
		s.queue[i].Priority += pointsPerSchedule
	}

	slices.SortFunc(s.queue, func(a, b SchedulerQueueEntry) int {
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
	for _, node := range s.nodes {
		allNodes = append(allNodes, node)
	}

	for queueIdx := 0; queueIdx < len(s.queue); queueIdx++ {
		entry := &s.queue[queueIdx]
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
				s.replicas = append(s.replicas, replicaEntry)
			}

			s.queue = append(s.queue[:queueIdx], s.queue[queueIdx+1:]...)
			queueIdx--
		}
	}

	s.time++
	return result
}

func (s *Scheduler) trySchedule(entry *SchedulerQueueEntry, allNodes []*SchedulerNode) []string {
	var allocatedNodes []string

	slices.SortFunc(allNodes, func(a, b *SchedulerNode) int {
		cmp := numericCompares(a.Gpu, b.Gpu, a.Cpu, b.Cpu, a.Memory, b.Memory)
		if cmp != 0 {
			return cmp
		}

		return strings.Compare(a.Name, b.Name)
	})

outer:
	for len(allocatedNodes) < entry.Replicas {
		for _, node := range allNodes {
			if !node.Unschedulable && node.Satisfies(entry.SchedulerDimensions) {
				node.Subtract(entry.SchedulerDimensions)
				allocatedNodes = append(allocatedNodes, node.Name)
				continue outer
			}
		}
		break
	}

	if len(allocatedNodes) < entry.Replicas {
		for _, n := range allocatedNodes {
			s.nodes[n].Add(entry.SchedulerDimensions)
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
