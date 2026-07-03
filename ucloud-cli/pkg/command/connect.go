package command

import "fmt"

type ConnectCommand struct {
	Token  string `flag:"token" usage:"Token"`
	Server string `flag:"server" usage:"Server"`
}

var ConnectCommands = map[string]CommandFunc{
	"connect": func() Command { return &ConnectCommand{} },
}

func (c *ConnectCommand) Execute() error {
	return fmt.Errorf("connect not implemented")
}
