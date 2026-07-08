package shared

import (
	"testing"

	orc "ucloud.dk/shared/pkg/orchestrators"
)

func TestValidateFileMountPath(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		ok       bool
		expected string
	}{
		{name: "empty is allowed", input: "", ok: true, expected: ""},
		{name: "relative is rejected", input: "tmp/data", ok: false},
		{name: "reserved etc path", input: "/etc/ucloud", ok: false},
		{name: "reserved opt path", input: "/opt/ucloud", ok: false},
		{name: "reserved work root", input: "/work", ok: false},
		{name: "reserved work child", input: "/work/data", ok: false},
		{name: "normal absolute path", input: "/mnt/input", ok: true, expected: "/mnt/input"},
		{name: "normalized absolute path", input: "/mnt/../mnt/input", ok: true, expected: "/mnt/input"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := ValidateFileMountPath(tt.input)
			if ok != tt.ok {
				t.Fatalf("expected ok=%v, got %v", tt.ok, ok)
			}

			if got != tt.expected {
				t.Fatalf("expected normalized path '%s', got '%s'", tt.expected, got)
			}
		})
	}
}

func TestValidateExplicitFileMountPaths(t *testing.T) {
	values := []orc.AppParameterValue{
		orc.AppParameterValueFileWithMountPath("drive-1/a", false, "/mount/a"),
		orc.AppParameterValueFileWithMountPath("drive-1/b", false, "/mount/b"),
	}

	ok, reason := ValidateExplicitFileMountPaths(values)
	if !ok {
		t.Fatalf("expected valid explicit mounts, got error: %s", reason)
	}

	values = append(values, orc.AppParameterValueFileWithMountPath("drive-1/c", false, "/mount/a"))
	ok, reason = ValidateExplicitFileMountPaths(values)
	if ok {
		t.Fatalf("expected conflicting explicit mounts to fail")
	}

	if reason == "" {
		t.Fatalf("expected conflict reason to be present")
	}
}

func TestContainerPathToUCloudFileMount(t *testing.T) {
	job := &orc.Job{}
	job.Specification.Resources = []orc.AppParameterValue{
		orc.AppParameterValueFile("/4/Foobar/Baz", false),
		orc.AppParameterValueFile("/4/Other/Data", false),
		orc.AppParameterValueFileWithMountPath("/4/Explicit/Input", false, "/mnt/input"),
	}
	job.Specification.Parameters = map[string]orc.AppParameterValue{
		"param": orc.AppParameterValueFile("/4/Param/Value", false),
	}

	tests := []struct {
		name     string
		input    string
		expected string
		ok       bool
	}{
		{name: "default exact", input: "/work/Baz", expected: "/4/Foobar/Baz", ok: true},
		{name: "default child", input: "/work/Baz/result.txt", expected: "/4/Foobar/Baz/result.txt", ok: true},
		{name: "parameter mount", input: "/work/Value", expected: "/4/Param/Value", ok: true},
		{name: "explicit child", input: "/mnt/input/file.txt", expected: "/4/Explicit/Input/file.txt", ok: true},
		{name: "unknown", input: "/work/Missing", ok: false},
		{name: "relative", input: "work/Baz", ok: false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := ContainerPathToUCloudFileMount(job, tt.input)
			if ok != tt.ok {
				t.Fatalf("expected ok=%v, got %v", tt.ok, ok)
			}

			if got != tt.expected {
				t.Fatalf("expected '%s', got '%s'", tt.expected, got)
			}
		})
	}
}

func TestContainerPathToUCloudFileMountDuplicateTitles(t *testing.T) {
	job := &orc.Job{}
	job.Specification.Resources = []orc.AppParameterValue{
		orc.AppParameterValueFile("/4/A/Data", false),
		orc.AppParameterValueFile("/4/B/Data", false),
	}

	got, ok := ContainerPathToUCloudFileMount(job, "/work/Data-1/result.txt")
	if !ok {
		t.Fatalf("expected duplicate title mount to resolve")
	}

	if got != "/4/B/Data/result.txt" {
		t.Fatalf("expected '/4/B/Data/result.txt', got '%s'", got)
	}
}
