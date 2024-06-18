package util

import (
	"fmt"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
)

type FileAndLine struct {
	File string
	Line int
}

func (f FileAndLine) String() string {
	return fmt.Sprintf("%s:%d", f.File, f.Line)
}

func GetCaller() FileAndLine {
	return GetCallerSkip(3)
}

func GetCallerSkip(skipCount int) FileAndLine {
	_, file, line, ok := runtime.Caller(skipCount)
	if !ok {
		return FileAndLine{
			File: "Unknown",
			Line: 1,
		}
	} else {
		return FileAndLine{
			File: stripProjectDir(file),
			Line: line,
		}
	}
}

func stripProjectDir(path string) string {
	var relevantComponents []string
	components := strings.Split(path, string(filepath.Separator))
	last := false
	for i := len(components) - 1; i >= 0; i-- {
		relevantComponents = append(relevantComponents, components[i])

		if last {
			break
		}

		if components[i] == "pkg" || components[i] == "cmd" {
			last = true
		}
	}

	slices.Reverse(relevantComponents)
	return strings.Join(relevantComponents, string(filepath.Separator))
}
