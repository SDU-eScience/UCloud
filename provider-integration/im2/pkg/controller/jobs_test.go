package controller

import (
	"testing"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func TestToHostnameSafe(t *testing.T) {
	testCases := []struct {
		input    string
		expected string
	}{
		// Basic cases
		{"hello world", "hello-world"},
		{"hello   world", "hello-world"},
		{"", ""},                         // Empty string
		{"   ", ""},                      // Only spaces
		{"hello", "hello"},               // Already valid
		{"hello-world", "hello-world"},   // Already valid with hyphen
		{"HELLO-WORLD", "hello-world"},   // Already valid with hyphen, but uppercase
		{"hello--world", "hello--world"}, // Valid consecutive hyphens

		// Non-ASCII characters
		{"hello 🚀 world", "hello-rocket-world"},   // Skips non-ASCII
		{"你好 世界", "nihao-shijie"},                 // All non-ASCII
		{"hola! ¿cómo estás?", "hola-como-estas"}, // Mix of valid and non-valid

		// Edge cases
		{"-hello-", "-hello-"},                 // Keep intentional dashes
		{"--hello--", "--hello--"},             // Keep intentional dashes
		{"   hello   world   ", "hello-world"}, // Trim spaces and normalize
		{"hello\tworld", "hello-world"},        // Handle tabs as spaces
		{"hello\nworld", "hello-world"},        // Handle newlines as spaces

		// Invalid characters
		{"hello@world.com", "helloworldcom"}, // Remove special characters
		{"123-456*789", "123-456789"},        // Remove invalid characters but keep valid ones

		// Long input
		{"a string with many spaces    and invalid * characters!!", "a-string-with-many-spaces-and-invalid-characters"},
		{"     ---multiple---hyphens---and   spaces---   ", "---multiple---hyphens---and-spaces---"},
	}

	for i, tc := range testCases {
		t.Run(tc.input, func(t *testing.T) {
			result := ToHostnameSafe(tc.input)
			if result != tc.expected {
				t.Errorf("Test case %d failed: Input: %q | Expected: %q | Got: %q", i+1, tc.input, tc.expected, result)
			}
		})
	}
}

func TestJobForTrackingKeepsQueuedApiServerResources(t *testing.T) {
	job := orc.Job{
		Status: orc.JobStatus{State: orc.JobStateInQueue},
		Specification: orc.JobSpecification{
			Resources: []orc.AppParameterValue{
				orc.AppParameterValueFile("/123/path", true),
				orc.AppParameterApiServer("Inference", "https://example.com/v1", "uci-secret"),
			},
		},
		Updates: []orc.JobUpdate{
			{
				ResourceList: util.OptValue([]orc.AppParameterValue{
					orc.AppParameterApiServer("Inference", "https://example.com/v1", "uci-secret"),
				}),
			},
		},
	}

	tracked := jobForTracking(job)

	if len(tracked.Specification.Resources) != 2 {
		t.Fatalf("expected queued api_server resource to be retained")
	}
	if tracked.Specification.Resources[1].Type != orc.AppParameterValueTypeApiServer {
		t.Fatalf("expected api_server resource")
	}
	if len(tracked.Updates) != 1 || !tracked.Updates[0].ResourceList.Present || len(tracked.Updates[0].ResourceList.Value) != 0 {
		t.Fatalf("expected api_server update resources to be removed")
	}
}

func TestJobForTrackingRemovesApiServerResourcesAfterQueue(t *testing.T) {
	job := orc.Job{
		Status: orc.JobStatus{State: orc.JobStateRunning},
		Specification: orc.JobSpecification{
			Resources: []orc.AppParameterValue{
				orc.AppParameterValueFile("/123/path", true),
				orc.AppParameterApiServer("Inference", "https://example.com/v1", "uci-secret"),
			},
		},
	}

	sanitized := jobForTracking(job)

	if len(sanitized.Specification.Resources) != 1 {
		t.Fatalf("expected one persisted resource, got %d", len(sanitized.Specification.Resources))
	}
	if sanitized.Specification.Resources[0].Type != orc.AppParameterValueTypeFile {
		t.Fatalf("expected file resource to remain")
	}
}

func TestPublicIpHistoricalReleasesUsesLatestTerminalUpdate(t *testing.T) {
	first := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	last := first.Add(2 * time.Hour)
	jobs := []orc.Job{
		{
			Specification: orc.JobSpecification{Resources: []orc.AppParameterValue{orc.AppParameterValueNetwork("ip-1")}},
			Updates:       []orc.JobUpdate{{State: util.OptValue(orc.JobStateSuccess), Timestamp: fnd.Timestamp(first)}},
		},
		{
			Updates: []orc.JobUpdate{
				{ResourceList: util.OptValue([]orc.AppParameterValue{orc.AppParameterValueNetwork("ip-1")}), Timestamp: fnd.Timestamp(first)},
				{State: util.OptValue(orc.JobStateFailure), Timestamp: fnd.Timestamp(last)},
			},
		},
	}

	releases, referenced := publicIpHistoricalReleases(jobs)
	if !referenced["ip-1"] {
		t.Fatal("expected IP to be found in job resource history")
	}
	if !releases["ip-1"].Equal(last) {
		t.Fatalf("expected latest terminal time %v, got %v", last, releases["ip-1"])
	}
}

func TestPublicIpHistoricalReleasesIgnoresNonTerminalUpdates(t *testing.T) {
	when := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	jobs := []orc.Job{{
		Specification: orc.JobSpecification{Parameters: map[string]orc.AppParameterValue{"ip": orc.AppParameterValueNetwork("ip-1")}},
		Updates:       []orc.JobUpdate{{State: util.OptValue(orc.JobStateRunning), Timestamp: fnd.Timestamp(when)}},
	}}

	releases, referenced := publicIpHistoricalReleases(jobs)
	if !referenced["ip-1"] {
		t.Fatal("expected IP to be found in job parameters")
	}
	if _, ok := releases["ip-1"]; ok {
		t.Fatal("non-terminal update must not create a release timestamp")
	}
}
