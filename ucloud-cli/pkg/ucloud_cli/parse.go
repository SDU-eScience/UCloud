package ucloud_cli

import (
	"fmt"

	com "ucloud.dk/ucloud_cli/pkg/command"
	"ucloud.dk/ucloud_cli/pkg/parsing"
	"ucloud.dk/ucloud_cli/pkg/utils"
)

// Top level command parserRegistry
var parserRegistry = map[string]parsing.Parser{
	"compute":     parsing.ParseCompute,
	"workspace":   func([]string) (com.Command, error) { return nil, nil },
	"environment": func([]string) (com.Command, error) { return nil, nil },
	"connect":     func([]string) (com.Command, error) { return nil, nil },
}

func Parse(args []string) (com.Command, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("no command")
	}

	tail, commandPhrase := utils.Consume(args)
	parser, ok := parserRegistry[commandPhrase]
	if !ok {
		return nil, fmt.Errorf("unknown command: %s", commandPhrase)
	}

	return parser(tail)
}
