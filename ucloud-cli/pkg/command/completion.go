package command

import (
	"bytes"
	"fmt"
	"reflect"
	"sort"
	"strings"

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
	Flags       []FlagSpec
	Notes       []string
	SubCommands []CommandSpec
}

type completionData struct {
	Routes []string
	Groups []completionGroup
	Leaves []completionLeaf
}

type completionGroup struct {
	Path     string
	Word     int
	Children []string
}

type completionLeaf struct {
	Path        string
	PathEnd     int
	FirstWord   int
	Positionals []completionPositional
	FlagsWord   int
	Flags       []string
}

type completionPositional struct {
	Word int
	Spec string
}

func commandsToCommandSpec() ([]CommandSpec, error) {
	tree := CommandTree()
	specs := make([]CommandSpec, 0, len(tree))
	for name, node := range tree {
		spec, err := commandSpecFromNode(name, node)
		if err != nil {
			return nil, err
		}
		specs = append(specs, spec)
	}
	sort.Slice(specs, func(i, j int) bool {
		return specs[i].Name < specs[j].Name
	})
	return specs, nil
}

func commandSpecFromNode(name string, node *CommandNode) (CommandSpec, error) {
	spec := CommandSpec{Name: name}
	if node.Factory != nil {
		cmd := node.Factory()
		flags, notes, err := commandFlagsAndNotes(cmd)
		if err != nil {
			return CommandSpec{}, err
		}
		spec.Flags = flags
		spec.Notes = notes
	}

	for childName, child := range node.Children {
		childSpec, err := commandSpecFromNode(childName, child)
		if err != nil {
			return CommandSpec{}, err
		}
		spec.SubCommands = append(spec.SubCommands, childSpec)
	}
	sort.Slice(spec.SubCommands, func(i, j int) bool {
		return spec.SubCommands[i].Name < spec.SubCommands[j].Name
	})
	return spec, nil
}

func commandFlagsAndNotes(cmd Command) ([]FlagSpec, []string, error) {
	v := reflect.ValueOf(cmd)
	if v.Kind() != reflect.Ptr || v.Elem().Kind() != reflect.Struct {
		return nil, nil, fmt.Errorf("bind expects pointer to struct")
	}

	v = v.Elem()
	t := v.Type()
	flags := make([]FlagSpec, 0)
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		flagType := "flag"

		flagName := field.Tag.Get("flag")
		positional := field.Tag.Get("positional")
		if flagName == "" && positional == "" {
			continue
		}

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

		if flagType == "flag" {
			if short := field.Tag.Get("short"); short != "" {
				flags = append(flags, FlagSpec{
					Name:      "-" + short,
					Usage:     field.Tag.Get("usage"),
					InputType: field.Type.Kind().String(),
					Required:  field.Tag.Get("required") == "true",
					FlagType:  flagType,
				})
			}
		}
	}

	var notes []string
	if noted, ok := cmd.(HelpNotes); ok {
		notes = noted.HelpNotes()
	}
	return flags, notes, nil
}

func completionDataFromTree() (completionData, error) {
	tree := CommandTree()
	names := make([]string, 0, len(tree))
	for name := range tree {
		names = append(names, name)
	}
	sort.Strings(names)

	data := completionData{Routes: make([]string, 0, len(names))}
	for _, name := range names {
		data.Routes = append(data.Routes, completionEscape(name))
	}

	for _, name := range names {
		if err := completionCollectNode(&data, []string{name}, tree[name]); err != nil {
			return completionData{}, err
		}
	}
	return data, nil
}

func completionCollectNode(data *completionData, path []string, node *CommandNode) error {
	if len(node.Children) > 0 {
		childNames := make([]string, 0, len(node.Children))
		for name := range node.Children {
			childNames = append(childNames, name)
		}
		sort.Strings(childNames)

		group := completionGroup{
			Path: strings.Join(path, " "),
			Word: len(path) + 2,
		}
		for _, name := range childNames {
			group.Children = append(group.Children, completionEscape(name))
		}
		data.Groups = append(data.Groups, group)

		for _, name := range childNames {
			childPath := append(append([]string{}, path...), name)
			if err := completionCollectNode(data, childPath, node.Children[name]); err != nil {
				return err
			}
		}
		return nil
	}

	if node.Factory == nil {
		return nil
	}

	cmd := node.Factory()
	flags, _, err := commandFlagsAndNotes(cmd)
	if err != nil {
		return err
	}

	leaf := completionLeaf{
		Path:      strings.Join(path, " "),
		PathEnd:   len(path) + 1,
		FirstWord: len(path) + 2,
	}

	positionals := 0
	for _, flag := range flags {
		if flag.FlagType != "positional" {
			continue
		}
		leaf.Positionals = append(leaf.Positionals, completionPositional{
			Word: len(path) + 2 + positionals,
			Spec: completionEscape(flag.Name + ":" + flag.Usage),
		})
		positionals++
	}
	leaf.FlagsWord = len(path) + 2 + positionals

	for _, flag := range flags {
		if flag.FlagType == "positional" {
			continue
		}
		leaf.Flags = append(leaf.Flags, completionEscape(flag.Name+":"+flag.Usage))
	}

	data.Leaves = append(data.Leaves, leaf)
	return nil
}

func completionTemplate() (*gonjaexec.Template, error) {
	filters := gonjaexec.NewFilterSet(map[string]gonjaexec.FilterFunction{}).Update(gonjabuiltins.Filters)

	env := &gonjaexec.Environment{
		Context:           gonjaexec.EmptyContext().Update(gonjabuiltins.GlobalFunctions),
		Filters:           filters,
		Tests:             gonjabuiltins.Tests,
		ControlStructures: gonjactrl.Safe,
		Methods:           gonjabuiltins.Methods,
	}

	gonjaCfg := gonjacfg.New()

	loader, err := gonjaload.NewFileSystemLoader("")
	if err != nil {
		return nil, err
	}
	shiftedLoader, err := gonjaload.NewShiftedLoader(
		"zsh_completion",
		bytes.NewReader(templates.TplZshCompletion),
		loader,
	)
	if err != nil {
		return nil, err
	}

	return gonjaexec.NewTemplate("zsh_completion", gonjaCfg, shiftedLoader, env)
}

func GenerateCompletionScript() error {
	data, err := completionDataFromTree()
	if err != nil {
		return err
	}

	tpl, err := completionTemplate()
	if err != nil {
		return err
	}

	params := map[string]interface{}{
		"routes": data.Routes,
		"groups": data.Groups,
		"leaves": data.Leaves,
	}
	str, err := tpl.ExecuteToString(gonjaexec.NewContext(params))
	if err != nil {
		return err
	}
	fmt.Print(str)

	return nil
}

func completionEscape(value string) string {
	value = strings.ReplaceAll(value, "\\", "\\\\")
	value = strings.ReplaceAll(value, "'", "'\\''")
	value = strings.ReplaceAll(value, "\n", " ")
	return value
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
