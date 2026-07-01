package command

import (
	"flag"
	"fmt"
	"reflect"
)

type Command interface {
	Execute() error
}

func Bind(args []string, cmd any) error {
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
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)

		flagName := field.Tag.Get("flag")
		if flagName == "" {
			continue
		}

		usage := field.Tag.Get("usage")

		binding := fieldBinding{
			index: i,
			kind:  field.Type.Kind(),
		}

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
