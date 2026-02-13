package termio

import (
	"fmt"
	"strings"
	"testing"
)

func TestTable(t *testing.T) {
	table := Table{}
	table.AppendHeader("Hello")
	table.AppendHeader("World")

	for i := 0; i < 30; i++ {
		table.Cell(strings.Repeat("Hello", 55))
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
