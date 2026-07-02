package command

import "fmt"

type SSHKeyListCommand struct{}

type SSHKeyAddCommand struct {
	Name  string `flag:"name" usage:"SSH key name"`
	Value string `flag:"value" usage:"SSH key value"`
}

type SSHKeyGetCommand struct {
	KeyId string `flag:"key-id" usage:"SSH key id"`
}

type SSHKeyDeleteCommand struct {
	KeyId string `flag:"key-id" usage:"SSH key id"`
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
