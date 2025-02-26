package controller

import "testing"

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
