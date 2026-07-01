package command

import (
	"fmt"
)

type ComputeProductsCommand struct {
	Provider string
	Verbose  bool
	Args     []string
}

func (c ComputeProductsCommand) Execute() error {
	// Render the command
	fmt.Println(c.Provider)
	return nil
}
