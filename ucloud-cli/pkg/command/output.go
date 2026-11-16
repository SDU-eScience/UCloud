package command

import (
	"fmt"
	"io"
	"os"
	"strings"

	"ucloud.dk/shared/pkg/termio"
)

func outputProgressWriter(machineReadable bool) io.Writer {
	if machineReadable {
		return os.Stderr
	}
	return os.Stdout
}

func outputSuccessText(output io.Writer, message string) string {
	check := termio.Success("✓").Render(termio.ColorEnabled(output))
	return fmt.Sprintf("%s %s", check, message)
}

func outputQuestionText(output io.Writer, message string) string {
	question := termio.StyledWith(0, termio.Blue, "?").Render(termio.ColorEnabled(output))
	return fmt.Sprintf("%s %s", question, message)
}

func outputLink(value string) string {
	if strings.TrimSpace(value) == "" {
		return "-"
	}
	return "↗ " + value
}

func outputTable(headers ...string) termio.Table {
	table := termio.Table{}
	for _, header := range headers {
		table.AppendHeader(header)
	}
	return table
}
