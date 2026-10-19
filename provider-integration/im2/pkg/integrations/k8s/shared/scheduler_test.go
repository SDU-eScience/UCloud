package shared

import (
	"testing"

	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func TestSchedulerDimensionsMapArithmetic(t *testing.T) {
	base := SchedulerDimensions{
		CpuMillis:     1000,
		MemoryInBytes: 2000,
		Resources: map[string]int{
			"nvidia.com/gpu":         3,
			"nvidia.com/mig-1g.10gb": 7,
		},
	}

	request := SchedulerDimensions{
		CpuMillis:     250,
		MemoryInBytes: 500,
		Resources: map[string]int{
			"nvidia.com/gpu": 1,
		},
	}

	sum := base.AddImmutable(request)
	if sum.CpuMillis != 1250 || sum.MemoryInBytes != 2500 {
		t.Fatalf("unexpected immutable add scalar result: %+v", sum)
	}
	if sum.Resources["nvidia.com/gpu"] != 4 || sum.Resources["nvidia.com/mig-1g.10gb"] != 7 {
		t.Fatalf("unexpected immutable add resources: %+v", sum.Resources)
	}

	base.Subtract(request)
	if base.CpuMillis != 750 || base.MemoryInBytes != 1500 {
		t.Fatalf("unexpected in-place subtract scalar result: %+v", base)
	}
	if base.Resources["nvidia.com/gpu"] != 2 || base.Resources["nvidia.com/mig-1g.10gb"] != 7 {
		t.Fatalf("unexpected in-place subtract resources: %+v", base.Resources)
	}
}

func TestSchedulerDimensionsSatisfiesChecksAllRequestedResources(t *testing.T) {
	available := SchedulerDimensions{
		CpuMillis:     8000,
		MemoryInBytes: 64 * 1000 * 1000 * 1000,
		Resources: map[string]int{
			"nvidia.com/gpu":         3,
			"nvidia.com/mig-1g.10gb": 7,
		},
	}

	okRequest := SchedulerDimensions{
		CpuMillis:     2000,
		MemoryInBytes: 4 * 1000 * 1000 * 1000,
		Resources: map[string]int{
			"nvidia.com/gpu":         2,
			"nvidia.com/mig-1g.10gb": 1,
		},
	}
	if !available.Satisfies(okRequest) {
		t.Fatalf("expected available resources to satisfy request")
	}

	missingKeyRequest := SchedulerDimensions{Resources: map[string]int{"amd.com/gpu": 1}}
	if available.Satisfies(missingKeyRequest) {
		t.Fatalf("expected missing resource key to fail satisfy check")
	}
}

func TestJobAntiAffinityGroupIsNamespacedByWorkspace(t *testing.T) {
	job := &orc.Job{}
	job.Specification.Labels = map[string]string{orc.ResourceLabelAntiAffinityGroup: "cluster-control-plane"}

	job.Owner.CreatedBy = "alice"
	group := JobAntiAffinityGroup(job)
	if group.Value != "alice/cluster-control-plane" {
		t.Errorf("expected a personal job to be namespaced by its user, got %q", group.Value)
	}

	job.Owner.Project = util.OptValue("project-1")
	group = JobAntiAffinityGroup(job)
	if group.Value != "project-1/cluster-control-plane" {
		t.Errorf("expected a project job to be namespaced by its project, got %q", group.Value)
	}

	job.Specification.Labels = map[string]string{}
	if JobAntiAffinityGroup(job).Present {
		t.Errorf("expected a job without the anti-affinity label to have no group")
	}
}
