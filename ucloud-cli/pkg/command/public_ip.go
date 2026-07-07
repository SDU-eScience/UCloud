package command

import "fmt"

type PublicIPListCommand struct {
}

type PublicIPGetCommand struct {
	Name string `positional:"name" usage:"IP name"`
}
type PublicIPDeleteCommand struct {
	Name string `positional:"name" usage:"IP name"`
}

type PublicIPCreateCommand struct {
	Name     string   `positional:"name" usage:"IP name"`
	Product  string   `flag:"product" usage:"Product"`
	OpenPort []string `flag:"open-port" usage:"Open port"`
}

var PublicIPCommands = map[string]CommandFunc{
	"list":   func() Command { return &PublicIPListCommand{} },
	"get":    func() Command { return &PublicIPGetCommand{} },
	"delete": func() Command { return &PublicIPDeleteCommand{} },
	"create": func() Command { return &PublicIPCreateCommand{} },
}

func (c PublicIPListCommand) Execute() error {
	return fmt.Errorf("public ip list not implemented")
}
func (c PublicIPGetCommand) Execute() error {
	return fmt.Errorf("public ip get not implemented")
}
func (c PublicIPDeleteCommand) Execute() error {
	return fmt.Errorf("public ip delete not implemented")
}
func (c PublicIPCreateCommand) Execute() error {
	return fmt.Errorf("public ip create not implemented")
}
