package command

import (
	"flag"
	"fmt"
	"io"
	"reflect"
	"strings"
)

type ArgsBindOptions struct {
	FlagSetName  string
	FlagsOnly    bool
	Interspersed bool
	ShortFlags   bool
	ErrorOutput  io.Writer
}

type ArgsBindResult struct {
	Positionals []string
	Visited     map[string]bool
}

type argsFieldBinding struct {
	index   int
	name    string
	kind    reflect.Kind
	strPtr  *string
	boolPtr *bool
	intPtr  *int
}

func ArgsBind(args []string, cmd any, options ArgsBindOptions) (ArgsBindResult, error) {
	var result ArgsBindResult
	if options.Interspersed && !options.FlagsOnly {
		return result, fmt.Errorf("interspersed flags require flags-only binding")
	}
	if len(args) == 0 && !options.FlagsOnly {
		return result, nil
	}

	v := reflect.ValueOf(cmd)
	if v.Kind() != reflect.Ptr || v.Elem().Kind() != reflect.Struct {
		return result, fmt.Errorf("bind expects pointer to struct")
	}

	v = v.Elem()
	t := v.Type()
	name := t.Name()
	if options.FlagSetName != "" {
		name = options.FlagSetName
	}
	fs := flag.NewFlagSet(name, flag.ContinueOnError)
	if options.ErrorOutput != nil {
		fs.SetOutput(options.ErrorOutput)
	}

	var bindings []argsFieldBinding
	aliases := map[string]string{}
	pos := 0
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		fieldValue := v.Field(i)
		flagName := field.Tag.Get("flag")

		positional := field.Tag.Get("positional")
		if !options.FlagsOnly && positional != "" {
			if pos >= len(args) {
				if field.Tag.Get("required") == "true" {
					return result, fmt.Errorf("missing required argument: %s", field.Name)
				}
				continue
			}

			if strings.HasPrefix(args[pos], "-") {
				if field.Tag.Get("required") == "true" {
					return result, fmt.Errorf("missing required argument: %s", field.Name)
				}
				continue
			}

			arg := args[pos]
			if field.Type.Kind() == reflect.Slice && field.Type.Elem().Kind() == reflect.String && !strings.HasPrefix(arg, "-") {
				for newPos, a := range args[pos:] {
					if strings.HasPrefix(a, "-") {
						pos = newPos - 1
						break
					}
					fieldValue.Set(reflect.Append(fieldValue, reflect.ValueOf(a)))
				}
			} else {
				v.Field(i).SetString(arg)
			}
			pos++
		}

		if !options.FlagsOnly && field.Tag.Get("default") != "" {
			v.Field(i).SetString(field.Tag.Get("default"))
		}
		required := field.Tag.Get("required") == "true"
		if flagName == "" {
			continue
		}

		usage := field.Tag.Get("usage")
		short := ""
		if options.ShortFlags {
			short = field.Tag.Get("short")
			if short != "" {
				aliases[short] = flagName
			}
		}

		if field.Type.Kind() == reflect.Slice && field.Type.Elem().Kind() == reflect.String {
			slicePtr := fieldValue.Addr().Interface().(*[]string)
			set := func(value string) error {
				*slicePtr = append(*slicePtr, value)
				return nil
			}
			fs.Func(flagName, usage, set)
			if short != "" {
				fs.Func(short, usage, set)
			}
			continue
		}

		if field.Type.Kind() == reflect.Map &&
			field.Type.Key().Kind() == reflect.String &&
			field.Type.Elem().Kind() == reflect.String {
			mapPtr := fieldValue.Addr().Interface().(*map[string]string)
			set := func(value string) error {
				parts := strings.SplitN(value, "=", 2)
				if len(parts) != 2 {
					return fmt.Errorf("expected key=value, got %q", value)
				}
				if *mapPtr == nil {
					*mapPtr = make(map[string]string)
				}
				(*mapPtr)[parts[0]] = parts[1]
				return nil
			}
			fs.Func(flagName, usage, set)
			if short != "" {
				fs.Func(short, usage, set)
			}
			continue
		}
		if required {
			return result, fmt.Errorf("required flag %s not implemented", flagName)
		}

		binding := argsFieldBinding{
			index: i,
			name:  flagName,
			kind:  field.Type.Kind(),
		}
		switch field.Type.Kind() {
		case reflect.String:
			if options.FlagsOnly {
				binding.strPtr = fieldValue.Addr().Convert(reflect.TypeOf((*string)(nil))).Interface().(*string)
				fs.StringVar(binding.strPtr, flagName, "", usage)
			} else {
				binding.strPtr = fs.String(flagName, "", usage)
			}
			if short != "" {
				fs.StringVar(binding.strPtr, short, "", usage)
			}

		case reflect.Bool:
			if options.FlagsOnly {
				binding.boolPtr = fieldValue.Addr().Convert(reflect.TypeOf((*bool)(nil))).Interface().(*bool)
				fs.BoolVar(binding.boolPtr, flagName, false, usage)
			} else {
				binding.boolPtr = fs.Bool(flagName, false, usage)
			}
			if short != "" {
				fs.BoolVar(binding.boolPtr, short, false, usage)
			}

		case reflect.Int:
			if options.FlagsOnly {
				binding.intPtr = fieldValue.Addr().Convert(reflect.TypeOf((*int)(nil))).Interface().(*int)
				fs.IntVar(binding.intPtr, flagName, 0, usage)
			} else {
				binding.intPtr = fs.Int(flagName, 0, usage)
			}
			if short != "" {
				fs.IntVar(binding.intPtr, short, 0, usage)
			}

		default:
			return result, fmt.Errorf("unsupported field type: %s", field.Type.Kind())
		}
		bindings = append(bindings, binding)
	}

	flagArgs := args[pos:]
	if options.Interspersed {
		flagArgs, result.Positionals = argsInterspersed(flagArgs, fs)
	}
	err := fs.Parse(flagArgs)
	if err != nil {
		return result, err
	}
	if !options.Interspersed {
		result.Positionals = fs.Args()
	}

	result.Visited = map[string]bool{}
	fs.Visit(func(f *flag.Flag) {
		result.Visited[f.Name] = true
		if name := aliases[f.Name]; name != "" {
			result.Visited[name] = true
		}
	})

	if options.FlagsOnly {
		return result, nil
	}
	for _, binding := range bindings {
		if !result.Visited[binding.name] {
			continue
		}
		field := v.Field(binding.index)
		switch binding.kind {
		case reflect.String:
			field.SetString(*binding.strPtr)
		case reflect.Bool:
			field.SetBool(*binding.boolPtr)
		case reflect.Int:
			field.SetInt(int64(*binding.intPtr))
		default:
			panic("unhandled default case")
		}
	}

	return result, nil
}

func argsInterspersed(args []string, fs *flag.FlagSet) ([]string, []string) {
	var flagArgs []string
	var positionals []string
	allowFlags := true
	for i := 0; i < len(args); i++ {
		arg := args[i]
		if allowFlags && arg == "--" {
			allowFlags = false
			continue
		}
		if !allowFlags || arg == "-" || !strings.HasPrefix(arg, "-") {
			positionals = append(positionals, arg)
			continue
		}

		flagArgs = append(flagArgs, arg)
		name, _, hasValue := strings.Cut(strings.TrimLeft(arg, "-"), "=")
		registered := fs.Lookup(name)
		if hasValue || registered == nil || i+1 >= len(args) {
			continue
		}
		boolean, ok := registered.Value.(interface{ IsBoolFlag() bool })
		if ok && boolean.IsBoolFlag() {
			continue
		}
		i++
		flagArgs = append(flagArgs, args[i])
	}
	return flagArgs, positionals
}
