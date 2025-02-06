package k8s

import (
	"fmt"
	"path/filepath"
	"testing"
)

func TestBasicScheduling(t *testing.T) {
	scheduler := NewScheduler()

	for i := 0; i < 10; i++ {
		scheduler.RegisterNode(fmt.Sprintf("node-%v", i), "example", 1000, 1000, 0, false)
	}

	fullNode := SchedulerDimensions{
		CpuMillis:     1000,
		MemoryInBytes: 1000,
	}

	for i := 0; i < 100; i++ {
		scheduler.RegisterJobInQueue(fmt.Sprint(i), "example", fullNode, 0, 1, 0)
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

func TestFie(t *testing.T) {
	f := filepath.Join("/fie/", "/er/en", "hund")
	fmt.Printf(f + "\n")
}
