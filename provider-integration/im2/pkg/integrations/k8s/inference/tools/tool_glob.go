package tools

import (
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strings"

	"ucloud.dk/shared/pkg/util"
)

type globPayload struct {
	Pattern string `json:"pattern"`
	Cwd     string `json:"cwd"`
	Limit   int    `json:"limit"`
}

func ToolGlob(payload string) {
	args := globPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		fmt.Fprintln(os.Stderr, "tool arguments must be valid JSON")
		return
	}
	if args.Cwd == "" {
		args.Cwd = "."
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 100
	}

	stdout, stderr, ok := util.RunCommand([]string{"rg", "--files", "--hidden", "--glob", args.Pattern, "--", args.Cwd})
	if !ok && stderr != "" {
		fmt.Fprintln(os.Stderr, stderr)
		return
	}

	matches := []string{}
	for _, path := range strings.Split(stdout, "\n") {
		if path != "" {
			matches = append(matches, path)
		}
	}
	sort.Strings(matches)
	if len(matches) > args.Limit {
		matches = matches[:args.Limit]
	}

	result, _ := json.Marshal(struct {
		Matches []string `json:"matches"`
		Count   int      `json:"count"`
	}{Matches: matches, Count: len(matches)})
	fmt.Println(string(result))
}
