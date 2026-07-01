package command

import (
	"fmt"
)

type ComputeProductsCommand struct {
	Provider string `flag:"provider" usage:"Provider name"`
	Verbose  bool   `flag:"verbose" usage:"Show verbose output"`
}

func (c ComputeProductsCommand) Execute() error {
	// Render the command
	fmt.Println(c.Provider)
	return nil
}
