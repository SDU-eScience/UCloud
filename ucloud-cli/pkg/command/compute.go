package command

import (
	"flag"
	"fmt"
)

type ComputeCommand struct {
	Command  string // products
	Provider string
	Flags    []string // --provider needs to be parsed
}

type ProductsCommand struct {
	Provider string
	Verbose  bool
	Args     []string
}

func (c ProductsCommand) run() error {
	//TODO implement me
	fmt.Println(c.Provider)
	return nil
}

func ParseCompute(args []string) (Command, error) {
	if len(args) < 2 || args[0] != "compute" {
		return nil, nil
	}

	switch args[1] {
	case "products":
		return ParseProducts(args[2:])
	default:
		return nil, fmt.Errorf("unknown subcommand: %s", args[1])
	}
}

func ParseProducts(args []string) (ProductsCommand, error) {
	fs := flag.NewFlagSet("products", flag.ContinueOnError)

	provider := fs.String("provider", "", "cloud provider")
	verbose := fs.Bool("verbose", false, "verbose output")

	if err := fs.Parse(args); err != nil {
		return ProductsCommand{}, err
	}
	if *provider == "" {
		return ProductsCommand{}, fmt.Errorf("--provider needs an argument")
	}

	return ProductsCommand{
		Provider: *provider,
		Verbose:  *verbose,
		Args:     fs.Args(),
	}, nil
}
