package command

import "fmt"

type EnvironmentUseCommand struct {
	Name string `flag:"name" usage:"Environment name"`
}

func (c EnvironmentUseCommand) Execute() error {
	return fmt.Errorf("environment use not implemented")
}

type EnvironmentListCommand struct {
}

func (c EnvironmentListCommand) Execute() error {
	return fmt.Errorf("environment list not implemented")
}

type EnvironmentAddCommand struct {
	Name  string `flag:"name" usage:"Environment name"`
	Value string `flag:"url" usage:"Environment value"`
}

func (c EnvironmentAddCommand) Execute() error {
	return fmt.Errorf("environment add not implemented")
}
