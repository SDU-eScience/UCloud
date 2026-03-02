package k8s

import (
	"fmt"
	"testing"
	"time"

	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func registerNodes(s *Scheduler, count, cpuMillis int) {
	for i := 0; i < count; i++ {
		dims := shared.SchedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: 1000,
		}
		s.RegisterNode(fmt.Sprintf("node-%v", i), dims, dims, false)
	}
}

func TestBasicScheduling(t *testing.T) {
	scheduler := NewScheduler("sched")

	registerNodes(scheduler, 10, 1000)
	fullNode := shared.SchedulerDimensions{
		CpuMillis:     1000,
		MemoryInBytes: 1000,
	}

	submitAt := fnd.Timestamp(time.Now())
	jobLength := orc.SimpleDuration{1, 0, 0}

	for i := 0; i < 100; i++ {
		scheduler.RegisterJobInQueue(fmt.Sprint(i), fullNode, 1, 0, submitAt, jobLength)
	}

	jobsSeen := map[string]bool{}
	for i := 0; i < 10; i++ {
		newJobs := scheduler.Schedule()
		nodesSeen := map[string]bool{}
		for _, j := range newJobs {
			jobsSeen[j.JobId] = true
			nodesSeen[j.Node] = true
		}

		if len(nodesSeen) != 10 {
			t.Errorf("Expected all nodes to be seen each schedule cycle, but didn't: %v", nodesSeen)
			return
		}

		scheduler.PruneReplicas()
	}

	if len(jobsSeen) != 100 {
		t.Errorf("Expected to see all jobs, but didn't: %v", jobsSeen)
	}

	for i := 0; i < 1000; i++ {
		newJobs := scheduler.Schedule()
		if len(newJobs) != 0 {
			t.Errorf("Expected no new jobs: %v", newJobs)
		}

		pruned := scheduler.PruneReplicas()
		if len(pruned) != 0 {
			t.Errorf("Expected no terminated jobs: %v", pruned)
		}
	}
}

func TestTimeInQueueAffectsPriority(t *testing.T) {
	s := NewScheduler("sched")
	s.WeightJobSize = 0
	s.WeightFairShare = 0

	now := time.Now()
	dimensions := shared.SchedulerDimensions{
		CpuMillis:     1000,
		MemoryInBytes: 1000,
	}
	duration := orc.SimpleDuration{Hours: 1}

	for i := 0; i < 10; i++ {
		s.RegisterJobInQueue(
			fmt.Sprint(i),
			dimensions,
			1,
			0,
			fnd.Timestamp(now.Add(time.Duration(-i)*time.Hour)),
			duration,
		)
	}

	s.Schedule()
	for i := 0; i < 10; i++ {
		actualJobId := s.Queue[i].JobId
		expectedJobId := fmt.Sprint(10 - 1 - i)
		if actualJobId != expectedJobId {
			t.Errorf("expected slot %v to contain %v but was %v", i, expectedJobId, actualJobId)
		}
	}

	if !util.FloatApproxEqual(1, s.Queue[0].Priority) {
		t.Errorf("expected front of queue to have priority equal to 1 but was %v", s.Queue[0].Priority)
	}

	if !util.FloatApproxEqual(0, s.Queue[9].Priority) {
		t.Errorf("expected tail of queue to have priority equal to 0 but was %v", s.Queue[9].Priority)
	}
}

func TestJobSizeAffectsPriority(t *testing.T) {
	type testConfig struct {
		FavorSmall     bool
		ChangeDims     bool
		ChangeReplicas bool
	}

	configs := []testConfig{
		{FavorSmall: true, ChangeDims: true, ChangeReplicas: false},
		{FavorSmall: true, ChangeDims: false, ChangeReplicas: true},
		{FavorSmall: true, ChangeDims: true, ChangeReplicas: true},

		{FavorSmall: false, ChangeDims: true, ChangeReplicas: false},
		{FavorSmall: false, ChangeDims: false, ChangeReplicas: true},
		{FavorSmall: false, ChangeDims: true, ChangeReplicas: true},
	}

	for _, config := range configs {
		s := NewScheduler("sched")
		s.WeightJobSize = 1
		s.WeightFairShare = 0
		s.WeightAge = 0

		if !config.FavorSmall {
			s.Flags |= SchedulerFavorLargeJobs
		}

		now := time.Now()
		duration := orc.SimpleDuration{Hours: 1}

		multiplierDims := 0
		multiplierReplicas := 0
		if config.ChangeReplicas {
			multiplierReplicas = 1
		}
		if config.ChangeDims {
			multiplierDims = 1000
		}

		count := 10
		for i := 0; i < count; i++ {
			factor := count - i
			if !config.FavorSmall {
				factor = i
			}

			s.RegisterJobInQueue(
				fmt.Sprint(i),
				shared.SchedulerDimensions{
					CpuMillis:     1000 + (multiplierDims * factor),
					MemoryInBytes: 1000 + (multiplierDims * factor),
				},
				1+(multiplierReplicas*factor),
				0,
				fnd.Timestamp(now),
				duration,
			)
		}

		s.Schedule()
		for i := 0; i < count; i++ {
			actualJobId := s.Queue[i].JobId
			expectedJobId := fmt.Sprint(count - 1 - i)
			if actualJobId != expectedJobId {
				t.Errorf("%v: expected slot %v to contain %v but was %v", config, i, expectedJobId, actualJobId)
			}
		}

		if !util.FloatApproxEqual(1, s.Queue[0].Priority) {
			t.Errorf("%v: expected front of queue to have priority equal to 1 but was %v", config, s.Queue[0].Priority)
		}

		if !util.FloatApproxEqual(0, s.Queue[9].Priority) {
			t.Errorf("%v: expected tail of queue to have priority equal to 0 but was %v", config, s.Queue[9].Priority)
		}

		if t.Failed() {
			return
		}
	}
}

func TestJobCompaction(t *testing.T) {
	testFn := func(flags SchedulerFlag, request, jobCount int) map[string]bool {
		s := NewScheduler("sched")
		cpuPerNode := 10000
		registerNodes(s, 10, cpuPerNode)
		s.Flags = flags

		for i := 0; i < jobCount; i++ {
			s.RegisterJobInQueue(
				fmt.Sprint(i),
				shared.SchedulerDimensions{CpuMillis: request},
				1,
				0,
				fnd.Timestamp(time.Now()),
				orc.SimpleDuration{Hours: 1},
			)
		}

		replicas := s.Schedule()
		if len(replicas) != jobCount {
			t.Errorf("expected to schedule all jobs, but didn't %v", replicas)
		}

		uniqueNodes := map[string]bool{}
		for _, replica := range replicas {
			uniqueNodes[replica.Node] = true
		}

		return uniqueNodes
	}

	type testConfig struct {
		Flags             SchedulerFlag
		Request           int
		JobCount          int
		ExpectedNodeCount int
	}

	configs := []testConfig{
		{Flags: 0, Request: 1000, JobCount: 10, ExpectedNodeCount: 1},
		{Flags: 0, Request: 1000, JobCount: 15, ExpectedNodeCount: 2},
		{Flags: 0, Request: 1000, JobCount: 20, ExpectedNodeCount: 2},
		{Flags: 0, Request: 1000, JobCount: 21, ExpectedNodeCount: 3},
		{Flags: 0, Request: 1000, JobCount: 30, ExpectedNodeCount: 3},
		{Flags: 0, Request: 1000, JobCount: 31, ExpectedNodeCount: 4},
		{Flags: 0, Request: 1000, JobCount: 40, ExpectedNodeCount: 4},
		{Flags: 0, Request: 1000, JobCount: 41, ExpectedNodeCount: 5},
		{Flags: 0, Request: 1000, JobCount: 50, ExpectedNodeCount: 5},
		{Flags: 0, Request: 1000, JobCount: 100, ExpectedNodeCount: 10},

		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 1, ExpectedNodeCount: 1},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 2, ExpectedNodeCount: 2},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 3, ExpectedNodeCount: 3},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 4, ExpectedNodeCount: 4},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 5, ExpectedNodeCount: 5},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 6, ExpectedNodeCount: 6},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 7, ExpectedNodeCount: 7},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 8, ExpectedNodeCount: 8},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 9, ExpectedNodeCount: 9},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 10, ExpectedNodeCount: 10},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 11, ExpectedNodeCount: 10},
		{Flags: SchedulerDisableJobCompaction, Request: 1000, JobCount: 100, ExpectedNodeCount: 10},

		{Flags: 0, Request: 8000, JobCount: 10, ExpectedNodeCount: 10},
		{Flags: SchedulerDisableJobCompaction, Request: 8000, JobCount: 10, ExpectedNodeCount: 10},
	}

	for _, config := range configs {
		nodes := testFn(config.Flags, config.Request, config.JobCount)
		if len(nodes) != config.ExpectedNodeCount {
			t.Errorf("expected all jobs to fit on a %v nodes, but didn't: %v", config.ExpectedNodeCount, nodes)
		}
	}
}

func TestFailingGpu(t *testing.T) {
	s := NewScheduler("sched")

	coreCount := 256
	memoryPerCore := 1024 * 1024 * 1024 * 4
	gpuCount := 8
	coresPerGpu := coreCount / gpuCount

	initialDims := shared.SchedulerDimensions{
		CpuMillis:     1000 * coreCount,
		MemoryInBytes: memoryPerCore * coreCount,
		Gpu:           gpuCount,
	}

	s.RegisterNode("gpu-node", initialDims, initialDims, false)

	submit := func(jobId string, gpuCount int) {
		s.RegisterJobInQueue(
			"initial",
			shared.SchedulerDimensions{
				CpuMillis:     1000 * coresPerGpu * gpuCount,
				MemoryInBytes: memoryPerCore * coresPerGpu * gpuCount,
				Gpu:           gpuCount,
			},
			1,
			0,
			fnd.Timestamp(time.Now()),
			orc.SimpleDuration{Hours: 1},
		)
	}

	submit("initial", 6)
	scheduled := s.Schedule()
	if len(scheduled) != 1 {
		t.Errorf("expected the initial job to be scheduled")
	}

	// Fail two of the GPUs
	failedDims := initialDims
	failedDims.Gpu -= 2
	s.RegisterNode("gpu-node", initialDims, failedDims, false)

	// Submit a new job
	submit("second", 2)
	scheduled = s.Schedule()
	if len(scheduled) != 0 {
		t.Errorf("expected the job to not immediately be scheduled")
	}

	// Recover the GPU
	s.RegisterNode("gpu-node", initialDims, initialDims, false)

	// Schedule again
	scheduled = s.Schedule()
	if len(scheduled) != 1 {
		t.Errorf("expected the job to be scheduled after recovery")
	}

	// Finish all jobs and fail the entire node
	s.PruneReplicas()
	s.RegisterNode("gpu-node", initialDims, shared.SchedulerDimensions{}, false)

	submit("third", 3)
	scheduled = s.Schedule()
	if len(scheduled) != 0 {
		t.Errorf("expected the third job not to schedule immediately")
	}

	// Recover the node, but make it unschedulable
	s.RegisterNode("gpu-node", initialDims, initialDims, true)
	scheduled = s.Schedule()
	if len(scheduled) != 0 {
		t.Errorf("expected the third job not to schedule on unschedulable")
	}

	// Recover the node fully
	s.RegisterNode("gpu-node", initialDims, initialDims, true)
	scheduled = s.Schedule()
	if len(scheduled) != 0 {
		t.Errorf("expected the third job to schedule in the end")
	}
}
