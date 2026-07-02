package command

import "fmt"

type EnvironmentUseCommand struct {
	Name string `flag:"name" usage:"Environment name"`
}
type EnvironmentListCommand struct {
}
type EnvironmentAddCommand struct {
	Name  string `flag:"name" usage:"Environment name"`
	Value string `flag:"url" usage:"Environment value"`
}

var EnvironmentCommands = map[string]CommandFunc{
	"use":  func() Command { return &EnvironmentUseCommand{} },
	"list": func() Command { return &EnvironmentListCommand{} },
	"add":  func() Command { return &EnvironmentAddCommand{} },
}

func (c EnvironmentUseCommand) Execute() error {
	return fmt.Errorf("environment use not implemented")
}
func (c EnvironmentListCommand) Execute() error {
	return fmt.Errorf("environment list not implemented")
}

func (c EnvironmentAddCommand) Execute() error {
	return fmt.Errorf("environment add not implemented")
}
