package command

import (
	"fmt"
	"reflect"
)

func GenerateCompletionScript() error {
	commands := CommandRegistry()
	for e, cmd := range commands {
		println(e)
		v := reflect.ValueOf(cmd)
		if v.Kind() != reflect.Ptr || v.Elem().Kind() != reflect.Struct {
			return fmt.Errorf("bind expects pointer to struct")
		}
		//println(v)
	}
	return nil
}

type CompletionGenerateCommand struct {
	Generate bool `flag:"generate" usage:"Generate completion script"`
}

var CompletionCommands = map[string]CommandFunc{
	"completion": func() Command { return &CompletionGenerateCommand{} },
}

func (c CompletionGenerateCommand) Execute() error {
	return GenerateCompletionScript()
}
