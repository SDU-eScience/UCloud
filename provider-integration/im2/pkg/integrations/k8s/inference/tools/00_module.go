package tools

import (
	"fmt"
	"os"
)

// NOTE(Dan): This package contains the implementation of the tools made available to the chat UI.

func Cli() {
	if len(os.Args) < 3 {
		fmt.Println("missing tool command or payload")
		os.Exit(1)
	}

	payload := os.Args[2]

	switch os.Args[1] {
	case "grep":
		ToolGrep(payload)
	case "glob":
		ToolGlob(payload)
	case "read":
		ToolRead(payload)
	case "web_fetch":
		ToolWebFetch(payload)
	case "wikipedia_search":
		ToolWikipediaSearch(payload)
	}
}
