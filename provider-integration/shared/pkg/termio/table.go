package termio

import (
	"fmt"
	"os"
	"strings"
	"unicode/utf8"
)

type Table struct {
	header []TableHeader
	rows   [][]string
}

type TableHeaderFlags = uint64

const (
	TableHeaderAlignRight TableHeaderFlags = 1 << iota
)

type TableHeader struct {
	Title string
	Flags TableHeaderFlags
}

func (t *Table) AppendHeader(title string) {
	t.header = append(t.header, TableHeader{title, 0})
}

func (t *Table) AppendHeaderEx(title string, flags TableHeaderFlags) {
	t.header = append(t.header, TableHeader{title, flags})
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
	os.Stdout.WriteString(t.String())
}

func (t *Table) String() string {
	ptyCols, _, isPty := safeQueryPtySize()

	largestColumn := make([]int, len(t.header))
	for _, row := range t.rows {
		for col, val := range row {
			if w := utf8.RuneCountInString(val); w > largestColumn[col] {
				largestColumn[col] = w
			}
		}
	}

	spaceAssignment := make([]int, len(t.header))
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

		rem := leftoverSpace % len(t.header)
		for col := 0; col < len(spaceAssignment); col++ {
			if rem <= 0 {
				break
			}

			rem -= 1
			spaceAssignment[col] = spaceAssignment[col] + 1
		}
	}

	builder := strings.Builder{}

	// Header top border
	{
		builder.WriteString(boxNwCorner)
		for col := range t.header {
			paddingRequired := spaceAssignment[col]
			if paddingRequired > 0 {
				builder.WriteString(strings.Repeat(boxHorizontalBar, paddingRequired+2))
			}
			if col == len(t.header)-1 {
				builder.WriteString(boxNeCorner)
			} else {
				builder.WriteString(boxHorizontalDown)
			}
		}
		builder.WriteString("\n")
	}

	// Header content
	{
		builder.WriteString(boxVerticalBar)
		for col, header := range t.header {
			paddingRequired := spaceAssignment[col] - utf8.RuneCountInString(header.Title)
			builder.WriteString(" ")
			builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, header.Title))
			if paddingRequired > 0 {
				builder.WriteString(strings.Repeat(" ", paddingRequired))
			}
			builder.WriteString(" ")
			builder.WriteString(boxVerticalBar)
		}
		builder.WriteString("\n")
	}

	// Header bottom border
	{
		builder.WriteString(boxVerticalRight)
		for col := range t.header {
			paddingRequired := spaceAssignment[col]
			if paddingRequired > 0 {
				builder.WriteString(strings.Repeat(boxHorizontalBar, paddingRequired+2))
			}
			if col == len(t.header)-1 {
				builder.WriteString(boxVerticalLeft)
			} else {
				if len(t.rows) == 0 {
					builder.WriteString(boxHorizontalUp)
				} else {
					builder.WriteString(boxCross)
				}
			}
		}
		builder.WriteString("\n")
	}

	if len(t.rows) == 0 {
		message := strings.Builder{}
		message.WriteString(boxVerticalBar)
		message.WriteString(" No data available")
		message.WriteString(strings.Repeat(" ", ptyCols-message.Len()+1))
		message.WriteString(boxVerticalBar)
		message.WriteString("\n")

		builder.WriteString(message.String())
	} else {
		for _, row := range t.rows {
			builder.WriteString(boxVerticalBar)
			for col, value := range row {
				colFlags := t.header[col].Flags

				paddingRequired := spaceAssignment[col] - utf8.RuneCountInString(value)
				if colFlags&TableHeaderAlignRight != 0 {
					builder.WriteString(" ")
					if paddingRequired > 0 {
						builder.WriteString(strings.Repeat(" ", paddingRequired))
					}
					builder.WriteString(value)
					builder.WriteString(" ")
					builder.WriteString(boxVerticalBar)
				} else {
					builder.WriteString(" ")
					builder.WriteString(value)
					if paddingRequired > 0 {
						builder.WriteString(strings.Repeat(" ", paddingRequired))
					}
					builder.WriteString(" ")
					builder.WriteString(boxVerticalBar)
				}
			}
			builder.WriteString("\n")
		}
	}

	// Table bottom border
	{
		builder.WriteString(boxSwCorner)
		for col := range t.header {
			paddingRequired := spaceAssignment[col]
			if paddingRequired > 0 {
				builder.WriteString(strings.Repeat(boxHorizontalBar, paddingRequired+2))
			}
			if col == len(t.header)-1 {
				builder.WriteString(boxSeCorner)
			} else {
				if len(t.rows) == 0 {
					builder.WriteString(boxHorizontalBar)
				} else {
					builder.WriteString(boxHorizontalUp)
				}
			}
		}
		builder.WriteString("\n")
	}

	return builder.String()
}
