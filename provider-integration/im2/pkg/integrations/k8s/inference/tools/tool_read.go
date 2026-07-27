package tools

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

type readPayload struct {
	Path   string `json:"path"`
	Offset int    `json:"offset"`
	Limit  int    `json:"limit"`
}

func ToolRead(payload string) {
	args := readPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		fmt.Fprintln(os.Stderr, "tool arguments must be valid JSON")
		return
	}
	if args.Offset <= 0 {
		args.Offset = 1
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 200
	}
	displayPath := filepath.Clean(args.Path)

	path, ok := readPath(args.Path)
	if !ok {
		fmt.Fprintln(os.Stderr, "failed to resolve path")
		return
	}

	info, err := os.Stat(path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	if info.IsDir() {
		readDirectory(path, displayPath, args.Limit)
		return
	}

	file, err := os.Open(path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	defer func() { _ = file.Close() }()

	prefix := make([]byte, 4096)
	prefixLen, err := file.Read(prefix)
	if err != nil && err != io.EOF {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	prefix = prefix[:prefixLen]
	if bytes.IndexByte(prefix, 0) >= 0 {
		fmt.Fprintln(os.Stderr, "file appears to be binary")
		return
	}

	scanner := bufio.NewScanner(io.MultiReader(bytes.NewReader(prefix), file))
	scanner.Buffer(make([]byte, 64*1024), 1024*1024)
	lines := []string{}
	lineNumber := 0
	truncated := false
	for scanner.Scan() {
		lineNumber++
		if lineNumber < args.Offset {
			continue
		}
		if len(lines) == args.Limit {
			truncated = true
			break
		}
		lines = append(lines, fmt.Sprintf("%d: %s", lineNumber, strings.ToValidUTF8(scanner.Text(), "\uFFFD")))
	}
	if err := scanner.Err(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}

	result, _ := json.Marshal(struct {
		Path      string `json:"path"`
		Offset    int    `json:"offset"`
		Lines     int    `json:"lines"`
		Content   string `json:"content"`
		Truncated bool   `json:"truncated"`
	}{
		Path:      displayPath,
		Offset:    args.Offset,
		Lines:     len(lines),
		Content:   strings.Join(lines, "\n"),
		Truncated: truncated,
	})
	fmt.Println(string(result))
}

func readPath(path string) (string, bool) {
	resolved, err := filepath.EvalSymlinks(path)
	return resolved, err == nil
}

func readDirectory(path string, displayPath string, limit int) {
	entries, err := os.ReadDir(path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}

	listing := make([]string, 0, min(len(entries), limit))
	for _, entry := range entries[:min(len(entries), limit)] {
		name := entry.Name()
		if entry.IsDir() {
			name += "/"
		}
		listing = append(listing, name)
	}

	result, _ := json.Marshal(struct {
		Path      string   `json:"path"`
		Entries   []string `json:"entries"`
		Count     int      `json:"count"`
		Truncated bool     `json:"truncated"`
	}{Path: displayPath, Entries: listing, Count: len(listing), Truncated: len(entries) > limit})
	fmt.Println(string(result))
}
