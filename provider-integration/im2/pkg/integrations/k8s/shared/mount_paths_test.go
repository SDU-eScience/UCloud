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
