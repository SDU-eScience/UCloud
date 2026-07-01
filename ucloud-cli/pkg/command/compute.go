package command

import (
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
