package command

import "fmt"

type AppListCommand struct{}
type AppSearchCommand struct {
	Application string `positional:"application" usage:"Application name"`
}

type AppGetCommand struct {
	Application string `positional:"application" usage:"Application name"`
}

var AppCommands = map[string]CommandFunc{
	"list":   func() Command { return &AppListCommand{} },
	"search": func() Command { return &AppSearchCommand{} },
	"get":    func() Command { return &AppGetCommand{} },
}

func (c AppListCommand) Execute() error {
	return fmt.Errorf("app list not implemented")
}

func (c AppSearchCommand) Execute() error {
	return fmt.Errorf("app search not implemented")
}

func (c AppGetCommand) Execute() error {
	return fmt.Errorf("app get not implemented")
}
