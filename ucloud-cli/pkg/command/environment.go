package command

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/shared"
)

type EnvironmentUseCommand struct {
	Name string `positional:"name" usage:"Environment name"`
}
type EnvironmentListCommand struct {
}
type EnvironmentAddCommand struct {
	Name string `positional:"name" usage:"Environment name" required:"true"`
	URL  string `flag:"url" usage:"Environment URL"`
}

type EnvironmentRemoveCommand struct {
	Name string `positional:"name" usage:"Environment name" required:"true"`
}

var EnvironmentCommands = map[string]CommandFunc{
	"use":    func() Command { return &EnvironmentUseCommand{} },
	"list":   func() Command { return &EnvironmentListCommand{} },
	"add":    func() Command { return &EnvironmentAddCommand{} },
	"remove": func() Command { return &EnvironmentRemoveCommand{} },
}

func (c EnvironmentUseCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	if cfg.DefaultEnvironment == c.Name {

		fmt.Printf("Already using %s as default environment\n", c.Name)
		shared.PrintCurrentEnvironment(cfg)
		return nil
	}
	// check if env exists
	_, ok := cfg.Environments[c.Name]
	if !ok {
		return fmt.Errorf("environment %s not found", c.Name)
	}
	cfg.DefaultEnvironment = c.Name
	updateCfg, updateErr := shared.UpdateConfig(cfg)
	if updateErr != nil {
		return updateErr
	}
	shared.PrintConfig(updateCfg)
	return nil
}
func (c EnvironmentListCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	fmt.Println("Environments")
	shared.PrintEnvironments(cfg)
	return nil
}

func (c EnvironmentAddCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	_, ok := cfg.Environments[c.Name]
	if ok {
		// updating the env if it already exists
		fmt.Printf("Updated environment of %s to %s \n", c.Name, c.URL)
		cfg.Environments[c.Name] = shared.Environment{
			URL: c.URL,
		}

	} else {
		// adding a new env
		fmt.Println("Adding new environment ", c.Name)
		cfg.Environments[c.Name] = shared.Environment{
			URL: c.URL,
		}
	}
	updateCfg, updateErr := shared.UpdateConfig(cfg)
	if updateErr != nil {
		return updateErr
	}
	shared.PrintEnvironments(updateCfg)
	return nil
}

func (c EnvironmentRemoveCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	if _, ok := cfg.Environments[c.Name]; !ok {
		return fmt.Errorf("environment %s was not found", c.Name)
	}
	delete(cfg.Environments, c.Name)
	updateCfg, updateErr := shared.UpdateConfig(cfg)
	if updateErr != nil {
		return updateErr
	}
	fmt.Printf("Environment %s has been removed\n", c.Name)
	shared.PrintEnvironments(updateCfg)
	return nil
}
