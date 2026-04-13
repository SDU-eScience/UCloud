package k8s

import (
	"fmt"
	"slices"
	"testing"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func registerNodes(s *Scheduler, count, cpuMillis int) {
	for i := 0; i < count; i++ {
		dims := shared.SchedulerDimensions{
			CpuMillis:     cpuMillis,
			MemoryInBytes: 1000,
			Resources:     map[string]int{},
		}
		s.RegisterNode(fmt.Sprintf("node-%v", i), dims, dims, false)
	}
}

func drainScheduleQueue() {
	for {
		entries := shared.SwapScheduleQueue()
		if len(entries) == 0 {
			return
		}
	}
}

func TestBasicScheduling(t *testing.T) {
	scheduler := NewScheduler("sched")

	registerNodes(scheduler, 10, 1000)
	fullNode := shared.SchedulerDimensions{
		CpuMillis:     1000,
		MemoryInBytes: 1000,
		Resources:     map[string]int{},
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
		Resources:     map[string]int{},
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
					Resources:     map[string]int{},
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
				shared.SchedulerDimensions{CpuMillis: request, Resources: map[string]int{}},
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
		Resources: map[string]int{
			shared.DefaultGpuResourceType: gpuCount,
		},
	}

	s.RegisterNode("gpu-node", initialDims, initialDims, false)

	submit := func(jobId string, gpuCount int) {
		s.RegisterJobInQueue(
			jobId,
			shared.SchedulerDimensions{
				CpuMillis:     1000 * coresPerGpu * gpuCount,
				MemoryInBytes: memoryPerCore * coresPerGpu * gpuCount,
				Resources: map[string]int{
					shared.DefaultGpuResourceType: gpuCount,
				},
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
	failedDims.Resources = map[string]int{shared.DefaultGpuResourceType: gpuCount - 2}
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
	s.RegisterNode("gpu-node", initialDims, shared.SchedulerDimensions{Resources: map[string]int{}}, false)

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
	s.RegisterNode("gpu-node", initialDims, initialDims, false)
	scheduled = s.Schedule()
	if len(scheduled) != 1 {
		t.Errorf("expected the third job to schedule in the end")
	}
}

func TestRequestScheduleQueueDeduplicatesByJob(t *testing.T) {
	drainScheduleQueue()
	t.Cleanup(drainScheduleQueue)

	jobAOld := &orc.Job{Resource: orc.Resource{Id: "job-a"}, Specification: orc.JobSpecification{Replicas: 1}}
	jobB := &orc.Job{Resource: orc.Resource{Id: "job-b"}, Specification: orc.JobSpecification{Replicas: 1}}
	jobANew := &orc.Job{Resource: orc.Resource{Id: "job-a"}, Specification: orc.JobSpecification{Replicas: 2}}

	shared.RequestSchedule(jobAOld)
	shared.RequestSchedule(jobB)
	shared.RequestSchedule(jobANew)

	entries := shared.SwapScheduleQueue()
	if len(entries) != 2 {
		t.Fatalf("expected exactly two queue entries, got %d", len(entries))
	}

	if entries[0] != jobANew {
		t.Fatalf("expected latest entry for job-a to be retained")
	}

	ids := []string{entries[0].Id, entries[1].Id}
	if !slices.Contains(ids, "job-a") || !slices.Contains(ids, "job-b") {
		t.Fatalf("expected entries to include both job-a and job-b, got %v", ids)
	}
}

func TestRequestScheduleQueueRetryDoesNotDuplicate(t *testing.T) {
	drainScheduleQueue()
	t.Cleanup(drainScheduleQueue)

	job := &orc.Job{Resource: orc.Resource{Id: "job-retry"}, Specification: orc.JobSpecification{Replicas: 1}}
	shared.RequestSchedule(job)

	firstSwap := shared.SwapScheduleQueue()
	if len(firstSwap) != 1 {
		t.Fatalf("expected one entry in first swap, got %d", len(firstSwap))
	}

	shared.RequestSchedule(firstSwap[0])
	shared.RequestSchedule(firstSwap[0])

	retrySwap := shared.SwapScheduleQueue()
	if len(retrySwap) != 1 {
		t.Fatalf("expected one entry after retry, got %d", len(retrySwap))
	}

	if retrySwap[0].Id != "job-retry" {
		t.Fatalf("expected retried job id to be job-retry, got %s", retrySwap[0].Id)
	}
}

func TestCpuStandardScenarioScheduling(t *testing.T) {
	shared.ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"cpu-standard": {
			Payment:                 cfg.PaymentInfo{Unit: cfg.MachineResourceTypeCpu},
			SystemReservedCpuMillis: util.OptValue(500),
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"full": {
					GroupName:  "full",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 1},
					Configs: []cfg.K8sMachineConfiguration{
						{AdvertisedCpu: 1, MemoryInGigabytes: 2},
						{AdvertisedCpu: 2, MemoryInGigabytes: 4},
						{AdvertisedCpu: 4, MemoryInGigabytes: 8},
						{AdvertisedCpu: 12, MemoryInGigabytes: 24},
					},
				},
				"fractional": {
					GroupName:  "fractional",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 4},
					Configs: []cfg.K8sMachineConfiguration{
						{AdvertisedCpu: 1, MemoryInGigabytes: 1},
						{AdvertisedCpu: 2, MemoryInGigabytes: 2},
						{AdvertisedCpu: 3, MemoryInGigabytes: 3},
					},
				},
			},
		},
	}

	fullProduct := &apm.ProductV2{
		Name:         "cpu-standard-1-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-standard"},
		Cpu:          1,
		MemoryInGigs: 2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 1},
	}

	fractionalProduct := &apm.ProductV2{
		Name:         "cpu-standard-2-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-standard"},
		Cpu:          2,
		MemoryInGigs: 2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 4},
	}

	if got := shared.NodeCpuMillisNormalizedWithReserved(fullProduct); got != 958 {
		t.Fatalf("expected full product CPU millis to be 958, got %d", got)
	}
	if got := shared.NodeCpuMillisNormalizedWithReserved(fractionalProduct); got != 479 {
		t.Fatalf("expected fractional product CPU millis to be 479, got %d", got)
	}

	fullDims := shared.JobDimensionsFromProductOnly(fullProduct)
	fractionalDims := shared.JobDimensionsFromProductOnly(fractionalProduct)

	if fullDims.CpuMillis != 958 {
		t.Fatalf("expected full product queue dims CPU to be 958, got %d", fullDims.CpuMillis)
	}
	if fractionalDims.CpuMillis != 479 {
		t.Fatalf("expected fractional product queue dims CPU to be 479, got %d", fractionalDims.CpuMillis)
	}

	node := shared.SchedulerDimensions{CpuMillis: 12000, MemoryInBytes: 200000000000, Resources: map[string]int{}}
	nodeLimits := shared.SchedulerDimensions{CpuMillis: 11500, MemoryInBytes: 200000000000, Resources: map[string]int{}}

	now := fnd.Timestamp(time.Now())
	jobLength := orc.SimpleDuration{Hours: 1}

	fullScheduler := NewScheduler("cpu-standard")
	fullScheduler.RegisterNode("node-0", node, nodeLimits, false)
	for i := 0; i < 13; i++ {
		fullScheduler.RegisterJobInQueue(fmt.Sprintf("full-%d", i), fullDims, 1, nil, now, jobLength)
	}

	fullScheduled := fullScheduler.Schedule()
	if len(fullScheduled) != 12 {
		t.Fatalf("expected 12 full jobs to be scheduled, got %d", len(fullScheduled))
	}
	if len(fullScheduler.Queue) != 1 {
		t.Fatalf("expected 1 full job to remain queued, got %d", len(fullScheduler.Queue))
	}

	fractionalScheduler := NewScheduler("cpu-standard")
	fractionalScheduler.RegisterNode("node-0", node, nodeLimits, false)
	for i := 0; i < 25; i++ {
		fractionalScheduler.RegisterJobInQueue(fmt.Sprintf("frac-%d", i), fractionalDims, 1, nil, now, jobLength)
	}

	fractionalScheduled := fractionalScheduler.Schedule()
	if len(fractionalScheduled) != 24 {
		t.Fatalf("expected 24 fractional jobs to be scheduled, got %d", len(fractionalScheduled))
	}
	if len(fractionalScheduler.Queue) != 1 {
		t.Fatalf("expected 1 fractional job to remain queued, got %d", len(fractionalScheduler.Queue))
	}
}

func TestMixedGpuResourceTypesCanCoexist(t *testing.T) {
	s := NewScheduler("gpu")

	nodeDims := shared.SchedulerDimensions{
		CpuMillis:     100000,
		MemoryInBytes: 1000 * 1000 * 1000 * 100,
		Resources: map[string]int{
			"nvidia.com/gpu":         3,
			"nvidia.com/mig-1g.10gb": 7,
		},
	}

	s.RegisterNode("node-0", nodeDims, nodeDims, false)

	for i := 0; i < 3; i++ {
		s.RegisterJobInQueue(
			fmt.Sprintf("full-%d", i),
			shared.SchedulerDimensions{
				CpuMillis:     1000,
				MemoryInBytes: 1000,
				Resources: map[string]int{
					"nvidia.com/gpu": 1,
				},
			},
			1,
			nil,
			fnd.Timestamp(time.Now()),
			orc.SimpleDuration{Hours: 1},
		)
	}

	for i := 0; i < 7; i++ {
		s.RegisterJobInQueue(
			fmt.Sprintf("mig-%d", i),
			shared.SchedulerDimensions{
				CpuMillis:     1000,
				MemoryInBytes: 1000,
				Resources: map[string]int{
					"nvidia.com/mig-1g.10gb": 1,
				},
			},
			1,
			nil,
			fnd.Timestamp(time.Now()),
			orc.SimpleDuration{Hours: 1},
		)
	}

	scheduled := s.Schedule()
	if len(scheduled) != 10 {
		t.Fatalf("expected 10 jobs to schedule, got %d", len(scheduled))
	}

	s.RegisterJobInQueue(
		"full-extra",
		shared.SchedulerDimensions{
			CpuMillis:     1000,
			MemoryInBytes: 1000,
			Resources: map[string]int{
				"nvidia.com/gpu": 1,
			},
		},
		1,
		nil,
		fnd.Timestamp(time.Now()),
		orc.SimpleDuration{Hours: 1},
	)

	s.RegisterJobInQueue(
		"mig-extra",
		shared.SchedulerDimensions{
			CpuMillis:     1000,
			MemoryInBytes: 1000,
			Resources: map[string]int{
				"nvidia.com/mig-1g.10gb": 1,
			},
		},
		1,
		nil,
		fnd.Timestamp(time.Now()),
		orc.SimpleDuration{Hours: 1},
	)

	if extra := s.Schedule(); len(extra) != 0 {
		t.Fatalf("expected no additional jobs to schedule, got %d", len(extra))
	}
}
