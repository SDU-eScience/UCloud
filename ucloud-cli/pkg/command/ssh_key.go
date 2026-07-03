package command

import "fmt"

type SSHKeyListCommand struct{}

type SSHKeyAddCommand struct {
	Name  string `positional:"name" usage:"SSH key name"`
	Value string `positional:"value" usage:"SSH key value"`
}

type SSHKeyGetCommand struct {
	Name string `positional:"name" usage:"SSH key name"`
}

type SSHKeyDeleteCommand struct {
	Name string `positional:"name" usage:"SSH key name"`
}

var SSHKeyCommands = map[string]CommandFunc{
	"list":   func() Command { return &SSHKeyListCommand{} },
	"add":    func() Command { return &SSHKeyAddCommand{} },
	"get":    func() Command { return &SSHKeyGetCommand{} },
	"delete": func() Command { return &SSHKeyDeleteCommand{} },
}

func (c SSHKeyListCommand) Execute() error {
	return fmt.Errorf("ssh key list not implemented")
}
func (c SSHKeyAddCommand) Execute() error {
	return fmt.Errorf("ssh key add not implemented")
}

func (c SSHKeyGetCommand) Execute() error {
	return fmt.Errorf("ssh key get not implemented")
}

func (c SSHKeyDeleteCommand) Execute() error {
	return fmt.Errorf("ssh key delete not implemented")
}
