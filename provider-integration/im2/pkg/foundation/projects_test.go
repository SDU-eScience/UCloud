package foundation

import (
	"testing"
	"unicode"
)

func TestProjectStartingWithDigit(t *testing.T) {
	parsed := GenerateProjectName("1-this-should-work")
	if len(parsed) == 0 {
		t.Errorf("Expected a non-empty suggested project name")
	} else {
		if unicode.IsDigit(rune(parsed[0])) {
			t.Error("did not expect first character to be a digit in suggested project name")
		}
	}
}
