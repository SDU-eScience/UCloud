package termio

import (
	"fmt"
	"os"
	"strings"
)

type Table struct {
	header []TableHeader
	rows   [][]string
}

type TableHeader struct {
	Title string
}

func (t *Table) AppendHeader(title string) {
	t.header = append(t.header, TableHeader{title})
}

func (t *Table) Cell(formatString string, args ...any) {
	if len(t.rows) == 0 {
		t.rows = append(t.rows, []string{})
	} else {
		currentRow := t.rows[len(t.rows)-1]
		if len(currentRow) == len(t.header) {
			t.rows = append(t.rows, []string{})
		}
	}

	t.rows[len(t.rows)-1] = append(t.rows[len(t.rows)-1], fmt.Sprintf(formatString, args...))
}

func (t *Table) Print() {
	_, _ = os.Stdout.WriteString(t.String())
}

func (t *Table) String() string {
	ptyCols, _, _ := safeQueryPtySize()

	var largestColumn []int
	for _, _ = range t.header {
		largestColumn = append(largestColumn, 0)
	}

	for _, row := range t.rows {
		for col, val := range row {
			if len(val) > largestColumn[col] {
				largestColumn[col] = len(val)
			}
		}
	}

	var spaceAssignment []int
	for _, _ = range t.header {
		spaceAssignment = append(spaceAssignment, 0)
	}
	minimumSpaceRequired := 1
	for col, colWidth := range largestColumn {
		spaceAssignment[col] = colWidth
		minimumSpaceRequired += colWidth + 3
	}

	if ptyCols > minimumSpaceRequired {
		leftoverSpace := ptyCols - minimumSpaceRequired
		spacePerColumn := leftoverSpace / len(t.header)
		for col := 0; col < len(spaceAssignment); col++ {
			spaceToAdd := min(leftoverSpace, spacePerColumn)
			leftoverSpace -= spaceToAdd
			spaceAssignment[col] = spaceAssignment[col] + spaceToAdd
		}
	}

	builder := strings.Builder{}
	builder.WriteString("|")
	for col, header := range t.header {
		paddingRequired := spaceAssignment[col] - len(header.Title)
		builder.WriteString(" ")
		builder.WriteString(header.Title)
		if paddingRequired > 0 {
			builder.WriteString(strings.Repeat(" ", paddingRequired))
		}
		builder.WriteString(" |")
	}
	builder.WriteString("\n")

	builder.WriteString("|")
	for col, _ := range t.header {
		paddingRequired := spaceAssignment[col]
		builder.WriteString(" ")
		if paddingRequired > 0 {
			builder.WriteString(strings.Repeat("-", paddingRequired))
		}
		builder.WriteString(" |")
	}
	builder.WriteString("\n")

	if len(t.rows) == 0 {
		builder.WriteString("No data available\n")
	} else {
		for _, row := range t.rows {
			builder.WriteString("|")
			for col, value := range row {
				paddingRequired := spaceAssignment[col] - len(value)
				builder.WriteString(" ")
				builder.WriteString(value)
				if paddingRequired > 0 {
					builder.WriteString(strings.Repeat(" ", paddingRequired))
				}
				builder.WriteString(" |")
			}
			builder.WriteString("\n")
		}
	}

	return builder.String()
}
