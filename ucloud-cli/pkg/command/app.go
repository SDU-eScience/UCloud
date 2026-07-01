package command

import "fmt"

func appHelp() string {
	return "app list | search | get"
}

type AppListCommand struct{}

func (c AppListCommand) Execute() error {
	return fmt.Errorf("app list not implemented")
}

type AppSearchCommand struct {
	Application string
}

func (c AppSearchCommand) Execute() error {
	return fmt.Errorf("app search not implemented")
}

type AppGetCommand struct {
	Application string
}

func (c AppGetCommand) Execute() error {
	return fmt.Errorf("app get not implemented")
}
