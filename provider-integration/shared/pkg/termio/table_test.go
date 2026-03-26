package termio

import (
	"fmt"
	"strings"
	"testing"
	"unicode/utf8"
)

func TestTable(t *testing.T) {
	table := Table{}
	table.AppendHeader("Hello")
	table.AppendHeader("World")

	for i := 0; i < 30; i++ {
		table.Cell("%s", strings.Repeat("Hello", 55))
		table.Cell("World%d", i)
	}

	fmt.Print(table.String())
}

func TestEmptyTable(t *testing.T) {
	table := Table{}
	table.AppendHeader("Hello")
	table.AppendHeader("World")
	fmt.Print(table.String())
}

func TestTableWrapsWithinWidth(t *testing.T) {
	table := Table{}
	table.AppendHeader("Column A")
	table.AppendHeader("Column B")

	table.Cell("alpha beta gamma delta epsilon")
	table.Cell("value-with-a-very-long-token-that-needs-hard-wrap")

	out := table.stringWithWidth(50, false)
	for _, line := range strings.Split(strings.TrimSuffix(out, "\n"), "\n") {
		if utf8.RuneCountInString(line) > 50 {
			t.Fatalf("line exceeds width: %q", line)
		}
	}
}
