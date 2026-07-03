package command

import "fmt"

type VMListCommand struct{}

type VMGetCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id"`
}

type VMDeleteCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id"`
}

type VMStopCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id"`
}

type VMShellCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id"`
}

type VMCreateCommand struct {
	Image          string `positional:"image" usage:"Image"`
	Product        string `flag:"product" usage:"Product"`
	Ssh            string `flag:"ssh" usage:"SSH"`
	PublicIp       string `flag:"public-ip" usage:"Public IP"`
	PrivateNetwork string `flag:"private-network" usage:"Private Network"`
}

var VMCommands = map[string]CommandFunc{
	"list":   func() Command { return &VMListCommand{} },
	"get":    func() Command { return &VMGetCommand{} },
	"delete": func() Command { return &VMDeleteCommand{} },
	"shell":  func() Command { return &VMShellCommand{} },
	"create": func() Command { return &VMCreateCommand{} },
	"stop":   func() Command { return &VMStopCommand{} },
}

func (c *VMListCommand) Execute() error {
	return fmt.Errorf("vm list not implemented")
}

func (c *VMGetCommand) Execute() error {
	return fmt.Errorf("vm get not implemented")
}

func (c *VMDeleteCommand) Execute() error {
	return fmt.Errorf("vm delete not implemented")
}

func (c *VMShellCommand) Execute() error {
	return fmt.Errorf("vm shell not implemented")
}

func (c *VMCreateCommand) Execute() error {
	return fmt.Errorf("vm create not implemented")
}

func (c *VMStopCommand) Execute() error {
	return fmt.Errorf("vm stop not implemented")
}
