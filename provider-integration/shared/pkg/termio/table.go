package termio

import (
	"fmt"
	"os"
	"strings"
	"unicode"
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
	return t.stringWithWidth(ptyCols, isPty)
}

func (t *Table) stringWithWidth(ptyCols int, isPty bool) string {
	if len(t.header) == 0 {
		return ""
	}

	preferredWidths := make([]int, len(t.header))
	for col, header := range t.header {
		preferredWidths[col] = utf8.RuneCountInString(header.Title)
	}

	for _, row := range t.rows {
		for col, val := range row {
			if col >= len(preferredWidths) {
				continue
			}

			if w := utf8.RuneCountInString(val); w > preferredWidths[col] {
				preferredWidths[col] = w
			}
		}
	}

	spaceAssignment := assignColumnWidths(ptyCols, preferredWidths)
	builder := strings.Builder{}

	// Header top border
	{
		builder.WriteString(boxNwCorner)
		for col := range t.header {
			builder.WriteString(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2))
			if col == len(t.header)-1 {
				builder.WriteString(boxNeCorner)
			} else {
				builder.WriteString(boxHorizontalDown)
			}
		}
		builder.WriteString("\n")
	}

	// Header content (may wrap)
	{
		headerLines := make([][]string, len(t.header))
		headerHeight := 1

		for col, header := range t.header {
			wrapped := wrapText(header.Title, spaceAssignment[col])
			headerLines[col] = wrapped
			headerHeight = max(headerHeight, len(wrapped))
		}

		for row := 0; row < headerHeight; row++ {
			builder.WriteString(boxVerticalBar)
			for col := range t.header {
				line := ""
				if row < len(headerLines[col]) {
					line = headerLines[col][row]
				}

				paddingRequired := max(0, spaceAssignment[col]-utf8.RuneCountInString(line))
				builder.WriteString(" ")
				builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, "%s", line))
				if paddingRequired > 0 {
					builder.WriteString(strings.Repeat(" ", paddingRequired))
				}
				builder.WriteString(" ")
				builder.WriteString(boxVerticalBar)
			}
			builder.WriteString("\n")
		}
	}

	// Header bottom border
	{
		builder.WriteString(boxVerticalRight)
		for col := range t.header {
			builder.WriteString(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2))
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
		padding := max(0, ptyCols-message.Len()+1)
		message.WriteString(strings.Repeat(" ", padding))
		message.WriteString(boxVerticalBar)
		message.WriteString("\n")

		builder.WriteString(message.String())
	} else {
		for _, row := range t.rows {
			wrappedCells := make([][]string, len(t.header))
			rowHeight := 1

			for col := range t.header {
				value := ""
				if col < len(row) {
					value = row[col]
				}

				wrapped := wrapText(value, spaceAssignment[col])
				wrappedCells[col] = wrapped
				rowHeight = max(rowHeight, len(wrapped))
			}

			for lineIdx := 0; lineIdx < rowHeight; lineIdx++ {
				builder.WriteString(boxVerticalBar)
				for col := range t.header {
					value := ""
					if lineIdx < len(wrappedCells[col]) {
						value = wrappedCells[col][lineIdx]
					}

					colFlags := t.header[col].Flags
					paddingRequired := max(0, spaceAssignment[col]-utf8.RuneCountInString(value))

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
	}

	// Table bottom border
	{
		builder.WriteString(boxSwCorner)
		for col := range t.header {
			builder.WriteString(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2))
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

func assignColumnWidths(ptyCols int, preferred []int) []int {
	widths := make([]int, len(preferred))
	copy(widths, preferred)

	if len(widths) == 0 {
		return widths
	}

	availableContent := max(1, ptyCols-(1+3*len(widths)))
	current := 0
	for i, w := range widths {
		widths[i] = max(1, w)
		current += widths[i]
	}

	minColumnWidth := 4
	if len(widths)*minColumnWidth > availableContent {
		minColumnWidth = 1
	}

	for current > availableContent {
		shrinkIdx := -1
		shrinkWidth := minColumnWidth

		for i, w := range widths {
			if w > shrinkWidth {
				shrinkIdx = i
				shrinkWidth = w
			}
		}

		if shrinkIdx < 0 {
			break
		}

		widths[shrinkIdx]--
		current--
	}

	if current < availableContent {
		leftover := availableContent - current
		spacePerColumn := leftover / len(widths)
		for i := range widths {
			grow := min(leftover, spacePerColumn)
			widths[i] += grow
			leftover -= grow
		}

		for i := range widths {
			if leftover <= 0 {
				break
			}
			widths[i]++
			leftover--
		}
	}

	return widths
}

func wrapText(value string, width int) []string {
	if width <= 0 {
		return []string{""}
	}

	lines := strings.Split(value, "\n")
	var result []string

	for _, line := range lines {
		if line == "" {
			result = append(result, "")
			continue
		}

		rest := line
		for utf8.RuneCountInString(rest) > width {
			chunk, tail := takeWrapChunk(rest, width)
			result = append(result, chunk)
			rest = strings.TrimLeftFunc(tail, unicode.IsSpace)
		}

		result = append(result, rest)
	}

	if len(result) == 0 {
		return []string{""}
	}

	return result
}

func takeWrapChunk(value string, width int) (string, string) {
	count := 0
	cutoff := len(value)
	lastSpaceEnd := -1

	for idx, r := range value {
		if unicode.IsSpace(r) {
			lastSpaceEnd = idx + utf8.RuneLen(r)
		}

		count++
		if count > width {
			cutoff = idx
			break
		}
	}

	if cutoff <= 0 {
		r, size := utf8.DecodeRuneInString(value)
		if r == utf8.RuneError && size == 0 {
			return "", ""
		}
		return value[:size], value[size:]
	}

	if lastSpaceEnd > 0 && lastSpaceEnd < cutoff {
		return strings.TrimRightFunc(value[:lastSpaceEnd], unicode.IsSpace), value[lastSpaceEnd:]
	}

	return value[:cutoff], value[cutoff:]
}
