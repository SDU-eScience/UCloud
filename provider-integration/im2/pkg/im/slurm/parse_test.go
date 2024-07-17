package slurm

import (
	"testing"
)

func TestArrayParsing(t *testing.T) {
	cliOutput := `JobID|State|User|Account|JobName|Partition|Elapsed|Timelimit|AllocTRES
123990|COMPLETED|user|uniuser|j_id1|fat|1-04:25:04|2-00:00:00|billing=1024,cpu=1024,mem=8000G,node=8
123000|COMPLETED|user|uniuser|j_id2|fat|20:44:13|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1
123001|COMPLETED|user|uniuser|j_id3|fat|20:40:08|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1
123002|COMPLETED|user|uniuser|j_id4|fat|15:16:57|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1
123003|COMPLETED|user|uniuser|j_id5|fat|15:12:35|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1
123004|COMPLETED|user|uniuser|j_id6|fat|15:07:40|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1
123005|COMPLETED|user|uniuser|j_id7|fat|14:57:56|2-00:00:00|billing=128,cpu=128,mem=1000G,node=1`

	var result []Job
	unmarshal(cliOutput, &result)

	if len(result) != 7 {
		t.Errorf("Expected 7 jobs, got %d", len(result))
		return
	}

	jobIds := []int{123990, 123000, 123001, 123002, 123003, 123004, 123005}
	for i, jobId := range jobIds {
		if result[i].JobID != jobId {
			t.Errorf("Expected job id %d, got %d", jobIds[i], result[i].JobID)
			return
		}
	}

	for i, job := range result {
		if i == 0 && job.AllocTRES["cpu"] != 1024 {
			t.Errorf("expected cpu 1024, got %d", job.AllocTRES["cpu"])
		}
		if i != 0 && job.AllocTRES["cpu"] != 128 {
			t.Errorf("expected cpu 128, got %d", job.AllocTRES["cpu"])
		}
	}
}

func TestElementParsing(t *testing.T) {
	cliOutput := `JobID|State|User|Account|JobName|Partition|Elapsed|Timelimit|AllocTRES
123990|COMPLETED|user|uniuser|j_id1|fat|1-04:25:04|2-00:00:00|billing=1024,cpu=1024,mem=8000G,node=8`

	var result Job
	unmarshal(cliOutput, &result)

	if result.JobID != 123990 {
		t.Errorf("Expected 123990, got %d", result.JobID)
	}

	if result.AllocTRES["cpu"] != 1024 {
		t.Errorf("Expected 1024, got %d", result.AllocTRES["cpu"])
	}
}
