package command

import "fmt"

type PublicLinkListCommand struct{}

type PublicLinkGetCommand struct {
	Name string `positional:"name" usage:"Public link name"`
}

type PublicLinkCreateCommand struct {
	Name    string `positional:"name" usage:"Public link name"`
	Domain  string `flag:"domain" usage:"Domain"`
	Product string `flag:"product" usage:"Product"`
}

type PublicLinkDeleteCommand struct {
	Name string `positional:"name" usage:"Public link name"`
}

var PublicLinkCommands = map[string]CommandFunc{
	"list": func() Command { return &PublicLinkListCommand{} },
	"get":  func() Command { return &PublicLinkGetCommand{} },
	"create": func() Command {
		return &PublicLinkCreateCommand{}
	},
	"delete": func() Command {
		return &PublicLinkDeleteCommand{}
	},
}

func (c PublicLinkListCommand) Execute() error {
	return fmt.Errorf("public link list not implemented")
}

func (c PublicLinkGetCommand) Execute() error {
	return fmt.Errorf("public link get not implemented")
}

func (c PublicLinkCreateCommand) Execute() error {
	return fmt.Errorf("public link create not implemented")
}

func (c PublicLinkDeleteCommand) Execute() error {
	return fmt.Errorf("public link delete not implemented")
}
