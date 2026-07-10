package tools

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"unicode/utf8"

	"ucloud.dk/shared/pkg/util"
)

type grepPayload struct {
	Pattern string `json:"pattern"`
	Path    string `json:"path"`
	Include string `json:"include"`
	Exclude string `json:"exclude"`
	Limit   int    `json:"limit"`
}

type grepRgOutput struct {
	Type string `json:"type"`
	Data struct {
		Path struct {
			Text string `json:"text"`
		} `json:"path"`
		Lines struct {
			Text string `json:"text"`
		} `json:"lines"`
		LineNumber int `json:"line_number"`
	} `json:"data"`
}

type grepMatch struct {
	Path string `json:"path"`
	Line int    `json:"line"`
	Text string `json:"text"`
}

func ToolGrep(payload string) {
	args := grepPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		fmt.Fprintln(os.Stderr, "tool arguments must be valid JSON")
		return
	}
	if args.Path == "" {
		args.Path = "."
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 100
	}

	command := []string{"rg", "--json", "--hidden", "--no-messages"}
	if args.Include != "" {
		command = append(command, "--glob", args.Include)
	}
	if args.Exclude != "" {
		command = append(command, "--glob", "!"+args.Exclude)
	}
	command = append(command, "--", args.Pattern, args.Path)

	stdout, stderr, ok := util.RunCommand(command)
	if !ok && stderr != "" {
		fmt.Fprintln(os.Stderr, stderr)
		return
	}

	matches := []grepMatch{}
	for _, line := range strings.Split(stdout, "\n") {
		entry := grepRgOutput{}
		if err := json.Unmarshal([]byte(line), &entry); err != nil || entry.Type != "match" {
			continue
		}

		matches = append(matches, grepMatch{
			Path: entry.Data.Path.Text,
			Line: entry.Data.LineNumber,
			Text: grepTrimLine(entry.Data.Lines.Text),
		})
		if len(matches) == args.Limit {
			break
		}
	}

	result, _ := json.Marshal(struct {
		Matches []grepMatch `json:"matches"`
		Count   int         `json:"count"`
	}{Matches: matches, Count: len(matches)})
	fmt.Println(string(result))
}

func grepTrimLine(line string) string {
	line = strings.TrimRight(line, "\r\n")
	if utf8.RuneCountInString(line) <= 1000 {
		return line
	}

	return string([]rune(line)[:1000])
}
