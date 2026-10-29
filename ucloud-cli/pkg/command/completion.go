package command

import (
	"fmt"
	"reflect"
	"sort"
	"strings"
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

func GenerateCompletionScript() error {
	specs, err := commandsToCommandSpec()
	if err != nil {
		return err
	}

	var builder strings.Builder
	builder.WriteString("#compdef ucloud\n\n")
	builder.WriteString("_ucloud() {\n")
	builder.WriteString("    if (( CURRENT == 2 )); then\n")
	builder.WriteString("        local -a routes\n")
	builder.WriteString("        routes=(")
	for _, spec := range specs {
		fmt.Fprintf(&builder, "'%s' ", completionEscape(spec.Name))
	}
	builder.WriteString(")\n")
	builder.WriteString("        compadd -a routes\n")
	builder.WriteString("        return\n")
	builder.WriteString("    fi\n\n")
	builder.WriteString("    case \"$words[2]\" in\n")
	for _, spec := range specs {
		fmt.Fprintf(&builder, "        %s)\n", spec.Name)
		writeCompletionNode(&builder, spec, 1, "            ")
		builder.WriteString("            ;;\n")
	}
	builder.WriteString("    esac\n")
	builder.WriteString("}\n\n")
	builder.WriteString("compdef _ucloud ucloud\n")
	fmt.Print(builder.String())
	return nil
}

func writeCompletionNode(builder *strings.Builder, spec CommandSpec, depth int, indent string) {
	if len(spec.SubCommands) > 0 {
		childWord := depth + 2
		fmt.Fprintf(builder, "%sif (( CURRENT == %d )); then\n", indent, childWord)
		fmt.Fprintf(builder, "%s    local -a subcommands\n", indent)
		fmt.Fprintf(builder, "%s    subcommands=(", indent)
		for _, child := range spec.SubCommands {
			fmt.Fprintf(builder, "'%s' ", completionEscape(child.Name))
		}
		builder.WriteString(")\n")
		fmt.Fprintf(builder, "%s    compadd -a subcommands\n", indent)
		fmt.Fprintf(builder, "%s    return\n", indent)
		fmt.Fprintf(builder, "%sfi\n", indent)
		fmt.Fprintf(builder, "%scase \"$words[%d]\" in\n", indent, childWord)
		for _, child := range spec.SubCommands {
			fmt.Fprintf(builder, "%s    %s)\n", indent, child.Name)
			writeCompletionNode(builder, child, depth+1, indent+"        ")
			fmt.Fprintf(builder, "%s        ;;\n", indent)
		}
		fmt.Fprintf(builder, "%sesac\n", indent)
		return
	}

	fmt.Fprintf(builder, "%slocal -a flags\n", indent)
	fmt.Fprintf(builder, "%sflags=()\n", indent)
	posWord := depth + 2
	for _, flag := range spec.Flags {
		if flag.FlagType != "positional" {
			continue
		}
		fmt.Fprintf(builder, "%sif (( %d == CURRENT )); then\n", indent, posWord)
		fmt.Fprintf(builder, "%s    flags+=( '%s:%s' '' )\n",
			indent,
			completionEscape(flag.Name),
			completionEscape(flag.Usage),
		)
		fmt.Fprintf(builder, "%sfi\n", indent)
		posWord++
	}
	for _, flag := range spec.Flags {
		if flag.FlagType == "positional" {
			continue
		}
		fmt.Fprintf(builder, "%sif (( CURRENT >= %d )); then\n", indent, posWord)
		fmt.Fprintf(builder, "%s    flags+=( '%s:%s' )\n",
			indent,
			completionEscape(flag.Name),
			completionEscape(flag.Usage),
		)
		fmt.Fprintf(builder, "%sfi\n", indent)
	}
	fmt.Fprintf(builder, "%s_describe 'flag' flags\n", indent)
	fmt.Fprintf(builder, "%sreturn\n", indent)
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
