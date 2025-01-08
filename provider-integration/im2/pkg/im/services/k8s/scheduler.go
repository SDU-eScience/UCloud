package k8s

import (
	"slices"
	"strings"
	"ucloud.dk/pkg/log"
)

type scheduler struct {
	PriorityPointsPerSchedule float64
	queue                     []schedulerQueueEntry
	replicas                  []schedulerReplicaEntry
	nodes                     map[string]*schedulerNode
	time                      int
}

func newScheduler(priorityPointsPerSchedule float64) *scheduler {
	return &scheduler{
		PriorityPointsPerSchedule: priorityPointsPerSchedule,
		nodes:                     make(map[string]*schedulerNode),
	}
}

type schedulerNode struct {
	Name     string
	Category string
	schedulerDimensions
	Initial       schedulerDimensions
	LastSeen      int
	Unschedulable bool
}

type schedulerDimensions struct {
	CpuMillis     int
	MemoryInBytes int
	Gpu           int
}

func (dims *schedulerDimensions) Add(other schedulerDimensions) {
	dims.CpuMillis += other.CpuMillis
	dims.MemoryInBytes += other.MemoryInBytes
	dims.Gpu += other.Gpu
}

func (dims *schedulerDimensions) Subtract(other schedulerDimensions) {
	dims.CpuMillis -= other.CpuMillis
	dims.MemoryInBytes -= other.MemoryInBytes
	dims.Gpu -= other.Gpu
}

func (dims *schedulerDimensions) Satisfies(request schedulerDimensions) bool {
	return dims.CpuMillis >= request.CpuMillis && dims.Gpu >= request.Gpu && dims.MemoryInBytes >= request.MemoryInBytes
}

type schedulerQueueEntry struct {
	JobId    string
	Category string
	schedulerDimensions
	Replicas int
	LastSeen int
	Priority float64
	Data     any
}

type schedulerReplicaEntry struct {
	JobId string
	Rank  int
	schedulerDimensions
	Node     string
	LastSeen int
	Data     any
}

func (s *scheduler) RegisterNode(name string, category string, cpuMillis int, memoryInBytes int, gpus int, unschedulable bool) {
	existing, ok := s.nodes[name]
	if ok {
		existing.LastSeen = s.time
		existing.Unschedulable = unschedulable
		existing.Category = category
		return
	}

	s.nodes[name] = &schedulerNode{
		Name:     name,
		Category: category,
		schedulerDimensions: schedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: memoryInBytes,
			Gpu:           gpus,
		},
		Initial: schedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: memoryInBytes,
			Gpu:           gpus,
		},
		LastSeen:      s.time,
		Unschedulable: unschedulable,
	}
}

func (s *scheduler) PruneNodes() []string {
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

func (s *scheduler) RegisterRunningReplica(
	jobId string,
	rank int,
	dimensions schedulerDimensions,
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

	s.replicas = append(s.replicas, schedulerReplicaEntry{
		JobId:               jobId,
		Rank:                rank,
		schedulerDimensions: dimensions,
		Node:                node,
		LastSeen:            s.time,
		Data:                data,
	})
}

func (s *scheduler) PruneReplicas() []schedulerReplicaEntry {
	var result []schedulerReplicaEntry

	now := s.time

	for i := 0; i < len(s.replicas); i++ {
		replica := &s.replicas[i]
		if replica.LastSeen == now {
			continue
		}

		// NOTE(Dan): The node can be nil if the replica lost its node
		node := s.nodes[replica.Node]
		if node != nil {
			dims := &node.schedulerDimensions
			dims.Add(replica.schedulerDimensions)
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

func (s *scheduler) RegisterJobInQueue(
	jobId string,
	category string,
	dimensions schedulerDimensions,
	priority float64,
	replicas int,
	data any,
) {
	s.queue = append(s.queue, schedulerQueueEntry{
		JobId:               jobId,
		Category:            category,
		schedulerDimensions: dimensions,
		Replicas:            replicas,
		LastSeen:            s.time,
		Data:                data,
		Priority:            priority,
	})
}

func (s *scheduler) RemoveJobFromQueue(jobId string) bool {
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

func (s *scheduler) Schedule() []schedulerReplicaEntry {
	var result []schedulerReplicaEntry

	pointsPerSchedule := s.PriorityPointsPerSchedule
	now := s.time
	queueLength := len(s.queue)

	if queueLength == 0 {
		return nil
	}

	for i := 0; i < queueLength; i++ {
		s.queue[i].Priority += pointsPerSchedule
	}

	slices.SortFunc(s.queue, func(a, b schedulerQueueEntry) int {
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

	var allNodes []*schedulerNode
	for _, node := range s.nodes {
		allNodes = append(allNodes, node)
	}

	for queueIdx := 0; queueIdx < len(s.queue); queueIdx++ {
		entry := &s.queue[queueIdx]
		allocatedNodes := s.trySchedule(entry, allNodes)

		if allocatedNodes != nil {
			for rank := 0; rank < len(allocatedNodes); rank++ {
				replicaEntry := schedulerReplicaEntry{
					JobId:               entry.JobId,
					Rank:                rank,
					schedulerDimensions: entry.schedulerDimensions,
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

func (s *scheduler) trySchedule(entry *schedulerQueueEntry, allNodes []*schedulerNode) []string {
	var allocatedNodes []string

	slices.SortFunc(allNodes, func(a, b *schedulerNode) int {
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

			if !node.Satisfies(entry.schedulerDimensions) {
				continue
			}

			node.Subtract(entry.schedulerDimensions)
			allocatedNodes = append(allocatedNodes, node.Name)
			continue outer
		}

		// NOTE(Dan): If we reach this point, we have tried all nodes and will not be able to succeed.
		// Break and fail soon after in the normal branch.
		break
	}

	if len(allocatedNodes) < entry.Replicas {
		for _, n := range allocatedNodes {
			s.nodes[n].Add(entry.schedulerDimensions)
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
