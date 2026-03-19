package ucx

import (
	"fmt"
	"math"
	"reflect"
	"strings"
	"unicode"

	"ucloud.dk/shared/pkg/log"
)

// StructToModel serializes a struct into a model map.
//
// Field names default to lowerCamelCase (for example JobName -> jobName).
// Use `ucx:"name"` to override a key and `ucx:"-"` to ignore a field.
// Nested structs are flattened using dot notation.
// Maps are serialized as ValueObject values.
func StructToModel(input any) (map[string]Value, error) {
	v := reflect.ValueOf(input)
	v = derefValue(v)
	if !v.IsValid() || v.Kind() != reflect.Struct {
		return nil, fmt.Errorf("ucx: StructToModel expects a struct, got %T", input)
	}

	out := map[string]Value{}
	if err := appendStructFields(out, "", v); err != nil {
		return nil, err
	}
	return out, nil
}

func StructToModelOrLog(input any) map[string]Value {
	result, err := StructToModel(input)
	if err != nil {
		log.Warn("Failed to serialize to value: %s", err)
		return map[string]Value{}
	}
	return result
}

func appendStructFields(out map[string]Value, prefix string, v reflect.Value) error {
	t := v.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		if field.PkgPath != "" {
			continue
		}

		name, omit, skip := parseUcxFieldTag(field.Tag.Get("ucx"), field.Name)
		if skip {
			continue
		}

		fv := v.Field(i)
		if omit && isZeroValue(fv) {
			continue
		}

		path := joinPath(prefix, name)
		if err := appendFlat(out, path, fv); err != nil {
			return fmt.Errorf("ucx: field %s: %w", field.Name, err)
		}
	}
	return nil
}

func appendFlat(out map[string]Value, path string, v reflect.Value) error {
	v = derefValue(v)
	if !v.IsValid() {
		out[path] = VNull()
		return nil
	}

	switch v.Kind() {
	case reflect.Struct:
		return appendStructFields(out, path, v)

	case reflect.Map:
		encoded, err := valueFromReflect(v)
		if err != nil {
			return err
		}
		out[path] = encoded
		return nil

	default:
		encoded, err := valueFromReflect(v)
		if err != nil {
			return err
		}
		out[path] = encoded
		return nil
	}
}

func valueFromReflect(v reflect.Value) (Value, error) {
	v = derefValue(v)
	if !v.IsValid() {
		return VNull(), nil
	}

	switch v.Kind() {
	case reflect.Bool:
		return VBool(v.Bool()), nil

	case reflect.String:
		return VString(v.String()), nil

	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return VS64(v.Int()), nil

	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64, reflect.Uintptr:
		u := v.Uint()
		if u > uint64(math.MaxInt64) {
			return Value{}, fmt.Errorf("unsigned integer %d overflows int64", u)
		}
		return VS64(int64(u)), nil

	case reflect.Float32, reflect.Float64:
		return Value{Kind: ValueF64, F64: v.Float()}, nil

	case reflect.Slice, reflect.Array:
		result := make([]Value, 0, v.Len())
		for i := 0; i < v.Len(); i++ {
			it, err := valueFromReflectInContainer(v.Index(i))
			if err != nil {
				return Value{}, err
			}
			result = append(result, it)
		}
		return VList(result), nil

	case reflect.Map:
		if v.Type().Key().Kind() != reflect.String {
			return Value{}, fmt.Errorf("unsupported map key type %s", v.Type().Key())
		}
		if v.IsNil() {
			return VObject(nil), nil
		}
		result := map[string]Value{}
		for _, k := range v.MapKeys() {
			it, err := valueFromReflectInContainer(v.MapIndex(k))
			if err != nil {
				return Value{}, err
			}
			result[k.String()] = it
		}
		return VObject(result), nil

	case reflect.Struct:
		result := map[string]Value{}
		t := v.Type()
		for i := 0; i < t.NumField(); i++ {
			field := t.Field(i)
			if field.PkgPath != "" {
				continue
			}

			name, omit, skip := parseUcxFieldTag(field.Tag.Get("ucx"), field.Name)
			if skip {
				continue
			}

			fv := v.Field(i)
			if omit && isZeroValue(fv) {
				continue
			}

			it, err := valueFromReflectInContainer(fv)
			if err != nil {
				return Value{}, fmt.Errorf("field %s: %w", field.Name, err)
			}
			result[name] = it
		}
		return VObject(result), nil

	case reflect.Interface:
		if v.IsNil() {
			return VNull(), nil
		}
		return valueFromReflect(v.Elem())

	default:
		return Value{}, fmt.Errorf("unsupported kind %s", v.Kind())
	}
}

func valueFromReflectInContainer(v reflect.Value) (Value, error) {
	v = derefValue(v)
	if !v.IsValid() {
		return VNull(), nil
	}
	return valueFromReflect(v)
}

func parseUcxFieldTag(tag string, fieldName string) (name string, omitEmpty bool, skip bool) {
	if tag == "-" {
		return "", false, true
	}

	name = toLowerCamel(fieldName)
	if tag == "" {
		return name, false, false
	}

	parts := strings.Split(tag, ",")
	if len(parts) > 0 && parts[0] != "" {
		name = parts[0]
	}

	for _, part := range parts[1:] {
		if part == "omitempty" {
			omitEmpty = true
		}
	}

	return name, omitEmpty, false
}

func derefValue(v reflect.Value) reflect.Value {
	for v.IsValid() && (v.Kind() == reflect.Pointer || v.Kind() == reflect.Interface) {
		if v.IsNil() {
			return reflect.Value{}
		}
		v = v.Elem()
	}
	return v
}

func joinPath(prefix string, name string) string {
	if prefix == "" {
		return name
	}
	return prefix + "." + name
}

func isZeroValue(v reflect.Value) bool {
	v = derefValue(v)
	if !v.IsValid() {
		return true
	}
	return v.IsZero()
}

func toLowerCamel(input string) string {
	if input == "" {
		return input
	}

	r := []rune(input)
	if len(r) == 1 {
		return string(unicode.ToLower(r[0]))
	}

	prefixEnd := 1
	if unicode.IsUpper(r[0]) && unicode.IsUpper(r[1]) {
		i := 0
		for i+1 < len(r) && unicode.IsUpper(r[i+1]) {
			i++
		}
		if i+1 < len(r) && unicode.IsLower(r[i+1]) {
			prefixEnd = i
		} else {
			prefixEnd = i + 1
		}
		if prefixEnd < 1 {
			prefixEnd = 1
		}
	}

	for i := 0; i < prefixEnd; i++ {
		r[i] = unicode.ToLower(r[i])
	}
	return string(r)
}
