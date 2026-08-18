package command

import "fmt"

type PrivateNetworkListCommand struct{}

type PrivateNetworkCreateCommand struct {
	Name      string `positional:"name" usage:"Private network name"`
	SubDomain string `flag:"sub-domain" usage:"Sub domain"`
	Product   string `flag:"product" usage:"Product"`
}
type PrivateNetworkGetCommand struct {
	Name string `positional:"name" usage:"Private network name"`
}

type PrivateNetworkDeleteCommand struct {
	Name string `positional:"name" usage:"Private network name"`
}

type PrivateNetworkMembersCommand struct {
	Name string `positional:"name" usage:"Private network name"`
}

var PrivateNetworkCommands = map[string]CommandFunc{
	"list":    func() Command { return &PrivateNetworkListCommand{} },
	"create":  func() Command { return &PrivateNetworkCreateCommand{} },
	"get":     func() Command { return &PrivateNetworkGetCommand{} },
	"delete":  func() Command { return &PrivateNetworkDeleteCommand{} },
	"members": func() Command { return &PrivateNetworkMembersCommand{} },
}

func (c PrivateNetworkListCommand) Execute() error {
	return fmt.Errorf("private network list not implemented")
}

func (c PrivateNetworkCreateCommand) Execute() error {
	return fmt.Errorf("private network create not implemented")
}

func (c PrivateNetworkGetCommand) Execute() error {
	return fmt.Errorf("private network get not implemented")
}

func (c PrivateNetworkDeleteCommand) Execute() error {
	return fmt.Errorf("private network delete not implemented")
}

func (c PrivateNetworkMembersCommand) Execute() error {
	return fmt.Errorf("private network members not implemented")
}
