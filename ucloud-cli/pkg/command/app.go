package command

import "fmt"

type AppSearchCommand struct {
	Application string `flag:"application" usage:"Application name"`
}

type AppGetCommand struct {
	Application string `flag:"application" usage:"Application name"`
}

var AppCommands = map[string]CommandFunc{
	"search": func() Command { return &AppSearchCommand{} },
	"get":    func() Command { return &AppGetCommand{} },
}

func (c AppSearchCommand) Execute() error {
	return fmt.Errorf("app search not implemented")
}

func (c AppGetCommand) Execute() error {
	return fmt.Errorf("app get not implemented")
}
