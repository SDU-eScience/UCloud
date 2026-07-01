package command

import "fmt"

type EnvironmentUseCommand struct {
	Name string
}

func (c EnvironmentUseCommand) Execute() error {
	return fmt.Errorf("environment use not implemented")
}

type EnvironmentListCommand struct{}

func (c EnvironmentListCommand) Execute() error {
	return fmt.Errorf("environment list not implemented")
}

type EnvironmentAddCommand struct {
	EnvName string
	URL     string
}

func (c EnvironmentAddCommand) Execute() error {
	return fmt.Errorf("environment add not implemented")
}
