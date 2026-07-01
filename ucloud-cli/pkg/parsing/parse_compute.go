package parsing

import (
	"flag"
	"fmt"

	com "ucloud.dk/ucloud_cli/pkg/command"
	"ucloud.dk/ucloud_cli/pkg/utils"
)

var computeParserRegistry = map[string]Parser{
	"products": ParseProducts,
}

// ParseProducts Caller should consume the first argument
func ParseProducts(args []string) (com.Command, error) {
	fs := flag.NewFlagSet("products", flag.ExitOnError)

	provider := fs.String("provider", "", "cloud provider")
	verbose := fs.Bool("verbose", false, "verbose output")

	if err := fs.Parse(args); err != nil {
		return com.ProductsCommand{}, err
	}

	return com.ProductsCommand{
		Provider: *provider,
		Verbose:  *verbose,
		Args:     fs.Args(),
	}, nil
}

// ParseCompute Caller should consume the first argument
func ParseCompute(args []string) (com.Command, error) {
	subCommand := utils.Peek(args)
	parser, ok := computeParserRegistry[subCommand]
	if !ok {
		return nil, fmt.Errorf("unknown subcommand: %s", subCommand)
	}
	return parser(args[1:])
}
