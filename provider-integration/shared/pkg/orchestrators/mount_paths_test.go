package orchestrators

import "testing"

func TestValidateFileMountPath(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		ok       bool
		expected string
	}{
		{name: "empty", input: "", ok: true, expected: ""},
		{name: "relative", input: "tmp/data", ok: false},
		{name: "reserved etc", input: "/etc/ucloud", ok: false},
		{name: "reserved opt", input: "/opt/ucloud", ok: false},
		{name: "reserved work", input: "/work", ok: false},
		{name: "reserved work child", input: "/work/a", ok: false},
		{name: "normal", input: "/mnt/data", ok: true, expected: "/mnt/data"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := ValidateFileMountPath(tt.input)
			if ok != tt.ok {
				t.Fatalf("expected ok=%v, got %v", tt.ok, ok)
			}

			if got != tt.expected {
				t.Fatalf("expected '%s', got '%s'", tt.expected, got)
			}
		})
	}
}

func TestValidateExplicitFileMountPathsConflict(t *testing.T) {
	values := []AppParameterValue{
		AppParameterValueFileWithMountPath("drive-1/a", false, "/mnt/a"),
		AppParameterValueFileWithMountPath("drive-1/b", false, "/mnt/a"),
	}

	ok, reason := ValidateExplicitFileMountPaths(values)
	if ok {
		t.Fatalf("expected conflict to fail")
	}

	if reason == "" {
		t.Fatalf("expected error reason")
	}
}
