package pystring

import (
	"fmt"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("Replace", func() {
	tests := []struct {
		input    PyString
		old      string
		new      string
		count    int
		expected PyString
	}{
		{input: "", old: "", new: "", count: -1, expected: ""},                 // Empty string, empty old, empty new
		{input: "", old: "", new: "abc", count: -1, expected: ""},              // Empty string, empty old, non-empty new
		{input: "", old: "abc", new: "", count: -1, expected: ""},              // Empty string, non-empty old, empty new
		{input: "", old: "abc", new: "def", count: -1, expected: ""},           // Empty string, non-empty old, non-empty new
		{input: "hello", old: "", new: "", count: -1, expected: "hello"},       // Non-empty string, empty old, empty new
		{input: "hello", old: "", new: "abc", count: -1, expected: "hello"},    // Non-empty string, empty old, non-empty new
		{input: "hello", old: "abc", new: "", count: -1, expected: "hello"},    // Non-empty string, non-empty old, empty new
		{input: "hello", old: "abc", new: "def", count: -1, expected: "hello"}, // Non-empty string, non-empty old, non-empty new
		{input: "hello", old: "l", new: "x", count: -1, expected: "hexxo"},     // Single replacement
		{input: "hello", old: "l", new: "x", count: 0, expected: "hello"},      // Zero count, no replacement
		{input: "hello", old: "l", new: "x", count: 1, expected: "hexlo"},      // One replacement
		{input: "hello", old: "l", new: "x", count: 2, expected: "hexxo"},      // Two replacements
		{input: "hello", old: "l", new: "x", count: 10, expected: "hexxo"},     // Count greater than occurrences
		{input: "hello", old: "l", new: "x", count: -1, expected: "hexxo"},     // Negative count, all replacements
	}

	for _, test := range tests {
		test := test // capture range variable
		It(fmt.Sprintf("For input '%s', old '%s', new '%s', count '%d', should return '%s'", test.input, test.old, test.new, test.count, test.expected), func() {
			Expect(test.input.Replace(test.old, test.new, test.count)).To(Equal(test.expected))
		})
	}
})
