package command

import (
	"fmt"
)

type ComputeProductsCommand struct {
	Provider string `flag:"provider" usage:"Provider name"`
	Verbose  bool   `flag:"verbose" usage:"Show verbose output"`
}

var ComputeCommands = map[string]CommandFunc{
	"products": func() Command { return &ComputeProductsCommand{} },
}

func (c ComputeProductsCommand) Execute() error {
	return fmt.Errorf("compute products not implemented")
}
