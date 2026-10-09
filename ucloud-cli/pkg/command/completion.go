package command

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"reflect"
	"sync"

	gonjabuiltins "ucloud.dk/gonja/v2/builtins"
	gonjactrl "ucloud.dk/gonja/v2/builtins/control_structures"
	gonjacfg "ucloud.dk/gonja/v2/config"
	gonjaexec "ucloud.dk/gonja/v2/exec"
	gonjaload "ucloud.dk/gonja/v2/loaders"
	"ucloud.dk/ucloud_cli/pkg/templates"
)

type FlagSpec struct {
	Name      string
	Usage     string
	InputType string
	Required  bool
	FlagType  string
}

type CommandSpec struct {
	Name        string
	SubCommands []SubCommand
}

type SubCommand struct {
	Name  string
	Flags []FlagSpec
}

type zshCompletionTemplate struct {
	tpl   *gonjaexec.Template
	mutex sync.Mutex
	data  []byte
}

func createZshCompletionTpl(source []byte) zshCompletionTemplate {
	return zshCompletionTemplate{
		data: source,
	}
}

func (tpl *zshCompletionTemplate) Template() *gonjaexec.Template {
	if tpl.tpl == nil {
		tpl.mutex.Lock()
		if tpl.tpl == nil {
			tpl.tpl = completionPrepareTemplateOrPanic(tpl.data)
		}
		tpl.mutex.Unlock()
	}
	return tpl.tpl
}

func completionPrepareTemplateOrPanic(byteSource []byte) *gonjaexec.Template {
	res, err := completionPrepareTemplate(byteSource)
	if err != nil {
		panic(err)
	}
	return res
}

func completionPrepareTemplate(byteSource []byte) (*gonjaexec.Template, error) {
	filters := gonjaexec.NewFilterSet(map[string]gonjaexec.FilterFunction{}).Update(gonjabuiltins.Filters)

	env := &gonjaexec.Environment{
		Context:           gonjaexec.EmptyContext().Update(gonjabuiltins.GlobalFunctions),
		Filters:           filters,
		Tests:             gonjabuiltins.Tests,
		ControlStructures: gonjactrl.Safe,
		Methods:           gonjabuiltins.Methods,
	}

	gonjaCfg := gonjacfg.New()
	gonjaCfg.AutoEscape = true

	rootID := fmt.Sprintf("root-%s", string(sha256.New().Sum(byteSource)))

	loader, err := gonjaload.NewFileSystemLoader("")
	if err != nil {
		return nil, err
	}
	shiftedLoader, err := gonjaload.NewShiftedLoader(rootID, bytes.NewReader(byteSource), loader)
	if err != nil {
		return nil, err
	}

	template, err := gonjaexec.NewTemplate(rootID, gonjaCfg, shiftedLoader, env)
	return template, err
}

func commandsToCommandSpec() ([]CommandSpec, error) {
	commands := CommandRegistry()
	specs := make([]CommandSpec, 0, len(commands))
	for cmdName, route := range commands {
		subCommands := make([]SubCommand, 0)

		for subCmdName, subCmdFunc := range route {
			flags := make([]FlagSpec, 0)
			subCmd := subCmdFunc()

			v := reflect.ValueOf(subCmd)
			if v.Kind() != reflect.Ptr || v.Elem().Kind() != reflect.Struct {
				return nil, fmt.Errorf("bind expects pointer to struct")
			}

			v = v.Elem()
			t := v.Type()

			for i := 0; i < t.NumField(); i++ {
				field := t.Field(i)
				flagType := "flag"
				//fieldValue := v.Field(i)

				flagName := field.Tag.Get("flag")
				// Handling positional arguments
				positional := field.Tag.Get("positional")
				name := "--" + flagName
				if positional != "" {
					flagType = "positional"
					name = positional
				}
				flag := FlagSpec{
					Name:      name,
					Usage:     field.Tag.Get("usage"),
					InputType: field.Type.Kind().String(),
					Required:  field.Tag.Get("required") == "true",
					FlagType:  flagType,
				}
				flags = append(flags, flag)
			}
			subCommands = append(subCommands, SubCommand{
				Name:  subCmdName,
				Flags: flags,
			})
		}
		specs = append(specs, CommandSpec{
			Name:        cmdName,
			SubCommands: subCommands,
		})
	}
	return specs, nil
}

func GenerateCompletionScript() error {
	specs, err := commandsToCommandSpec()
	if err != nil {
		return err
	}

	createdTpl := createZshCompletionTpl(templates.TplZshCompletion)
	params := map[string]interface{}{
		"commands": specs,
	}
	ctx := gonjaexec.NewContext(params)
	str, err := createdTpl.Template().ExecuteToString(ctx)
	if err != nil {
		return err
	}
	fmt.Print(str)

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
