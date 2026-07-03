package ucloud_cli

import (
	"flag"
	"fmt"
	"reflect"
	"strings"

	com "ucloud.dk/ucloud_cli/pkg/command"
)

func Peek(args []string) string {
	if len(args) == 0 {
		return ""
	}
	return args[0]
}

func Consume(args []string) ([]string, string) {
	if len(args) == 0 {
		return []string{}, ""
	}
	return args[1:], args[0]
}

func registerCommandParser() map[string]map[string]com.CommandFunc {
	registry := map[string]map[string]com.CommandFunc{}
	registry["app"] = com.AppCommands
	registry["workspace"] = com.WorkspaceCommands
	registry["compute"] = com.ComputeCommands
	registry["environment"] = com.EnvironmentCommands
	registry["ssh-key"] = com.SSHKeyCommands
	registry["job"] = com.JobCommands
	registry["vm"] = com.VMCommands
	registry["connect"] = com.ConnectCommands
	registry["public-ip"] = com.PublicIPCommands
	registry["public-link"] = com.PublicLinkCommands
	registry["private-network"] = com.PrivateNetworkCommands
	registry["folder"] = com.FolderCommands
	return registry
}

func bindCommand(args []string, cmd any) error {
	if len(args) == 0 {
		return nil
	}
	v := reflect.ValueOf(cmd)
	if v.Kind() != reflect.Ptr || v.Elem().Kind() != reflect.Struct {
		return fmt.Errorf("bind expects pointer to struct")
	}

	v = v.Elem()
	t := v.Type()

	type fieldBinding struct {
		index   int
		kind    reflect.Kind
		strPtr  *string
		boolPtr *bool
		intPtr  *int
	}

	var bindings []fieldBinding

	fs := flag.NewFlagSet(t.Name(), flag.ContinueOnError)

	// Register flags
	pos := 0
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		fieldValue := v.Field(i)

		flagName := field.Tag.Get("flag")

		// Handling positional arguments
		positional := field.Tag.Get("positional")
		if positional != "" {
			if pos >= len(args) {
				if field.Tag.Get("required") == "true" {
					return fmt.Errorf("missing required argument: %s", field.Name)
				}
				continue
			}

			v.Field(i).SetString(args[pos])
			pos++
		}

		required := field.Tag.Get("required") == "true"
		if flagName == "" {
			continue
		}

		usage := field.Tag.Get("usage")

		// For slice of strings
		if field.Type.Kind() == reflect.Slice &&
			field.Type.Elem().Kind() == reflect.String {

			slicePtr := fieldValue.Addr().Interface().(*[]string)

			fs.Func(flagName, usage, func(value string) error {
				*slicePtr = append(*slicePtr, value)
				return nil
			})

			continue
		}

		// Special case for map[string]string
		if field.Type.Kind() == reflect.Map &&
			field.Type.Key().Kind() == reflect.String &&
			field.Type.Elem().Kind() == reflect.String {

			mapPtr := fieldValue.Addr().Interface().(*map[string]string)
			fs.Func(flagName, usage, func(value string) error {
				parts := strings.SplitN(value, "=", 2)
				if len(parts) != 2 {
					return fmt.Errorf("expected key=value, got %q", value)
				}

				key := parts[0]
				val := parts[1]

				if *mapPtr == nil {
					*mapPtr = make(map[string]string)
				}

				(*mapPtr)[key] = val // ✅ accumulate, never replace map
				return nil
			})

			continue
		}
		if required {
			return fmt.Errorf("required flag %s not implemented", flagName)
		}

		binding := fieldBinding{
			index: i,
			kind:  field.Type.Kind()}

		switch field.Type.Kind() {
		case reflect.String:
			binding.strPtr = fs.String(flagName, "", usage)

		case reflect.Bool:
			binding.boolPtr = fs.Bool(flagName, false, usage)

		case reflect.Int:
			binding.intPtr = fs.Int(flagName, 0, usage)

		default:
			return fmt.Errorf("unsupported field type: %s", field.Type.Kind())
		}

		bindings = append(bindings, binding)
	}

	// Parse args
	err := fs.Parse(args)
	if err != nil {
		return err
	}

	// Assign values back into struct
	for _, b := range bindings {
		field := v.Field(b.index)

		switch b.kind {
		case reflect.String:
			field.SetString(*b.strPtr)

		case reflect.Bool:
			field.SetBool(*b.boolPtr)

		case reflect.Int:
			field.SetInt(int64(*b.intPtr))
		default:
			panic("unhandled default case")
		}
	}

	return nil
}

func Parse(commands []string) (com.Command, error) {
	if len(commands) == 0 {
		return nil, fmt.Errorf("no mainCommand")
	}

	commands, mainCommand := Consume(commands)
	subCommand := Peek(commands)

	commandParsers := registerCommandParser()

	parserRoute, ok := commandParsers[mainCommand]
	if !ok {
		return nil, fmt.Errorf("mainCommand %s not found", mainCommand)
	}

	createFunc, ok := parserRoute[subCommand]
	if !ok {
		return nil, fmt.Errorf("subcommand %s not found", subCommand)
	}
	cmd := createFunc()

	commands, _ = Consume(commands)

	err := bindCommand(commands, cmd)

	if err != nil {
		return nil, err
	}
	return cmd, nil
}
