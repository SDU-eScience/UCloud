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

// ModelToStruct deserializes a model map into a struct.
//
// Top-level keys are expected to use dot notation for nested structs
// (for example nested.field), matching StructToModel output.
func ModelToStruct(input map[string]Value, output any) error {
	v := reflect.ValueOf(output)
	if !v.IsValid() || v.Kind() != reflect.Pointer || v.IsNil() {
		return fmt.Errorf("ucx: ModelToStruct expects a non-nil pointer to struct, got %T", output)
	}

	for v.Kind() == reflect.Pointer {
		if v.IsNil() {
			v.Set(reflect.New(v.Type().Elem()))
		}
		v = v.Elem()
	}

	if !v.IsValid() || v.Kind() != reflect.Struct {
		return fmt.Errorf("ucx: ModelToStruct expects a pointer to struct, got %T", output)
	}

	if input == nil {
		return nil
	}

	return populateStructFromFlatModel(input, "", v)
}

func populateStructFromFlatModel(input map[string]Value, prefix string, out reflect.Value) error {
	t := out.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		if field.PkgPath != "" {
			continue
		}

		name, _, skip := parseUcxFieldTag(field.Tag.Get("ucx"), field.Name)
		if skip {
			continue
		}

		fieldPath := joinPath(prefix, name)
		fv := out.Field(i)

		if raw, ok := input[fieldPath]; ok {
			decoded, err := valueToReflect(raw, fv.Type())
			if err != nil {
				return fmt.Errorf("field %s: %w", field.Name, err)
			}
			fv.Set(decoded)
			continue
		}

		if isStructType(fv.Type()) && modelHasPrefix(input, fieldPath) {
			target, err := ensureStructValue(fv)
			if err != nil {
				return fmt.Errorf("field %s: %w", field.Name, err)
			}
			if err := populateStructFromFlatModel(input, fieldPath, target); err != nil {
				return fmt.Errorf("field %s: %w", field.Name, err)
			}
		}
	}

	return nil
}

func populateStructFromObjectModel(input map[string]Value, out reflect.Value) error {
	t := out.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		if field.PkgPath != "" {
			continue
		}

		name, _, skip := parseUcxFieldTag(field.Tag.Get("ucx"), field.Name)
		if skip {
			continue
		}

		raw, ok := input[name]
		if !ok {
			continue
		}

		decoded, err := valueToReflect(raw, out.Field(i).Type())
		if err != nil {
			return fmt.Errorf("field %s: %w", field.Name, err)
		}

		out.Field(i).Set(decoded)
	}

	return nil
}

func valueToReflect(input Value, targetType reflect.Type) (reflect.Value, error) {
	if input.Kind == ValueNull {
		return reflect.Zero(targetType), nil
	}

	if targetType.Kind() == reflect.Pointer {
		item, err := valueToReflect(input, targetType.Elem())
		if err != nil {
			return reflect.Value{}, err
		}
		ptr := reflect.New(targetType.Elem())
		ptr.Elem().Set(item)
		return ptr, nil
	}

	switch targetType.Kind() {
	case reflect.Bool:
		if input.Kind != ValueBool {
			return reflect.Value{}, fmt.Errorf("expected bool, got %v", input.Kind)
		}
		v := reflect.New(targetType).Elem()
		v.SetBool(input.Bool)
		return v, nil

	case reflect.String:
		if input.Kind != ValueString {
			return reflect.Value{}, fmt.Errorf("expected string, got %v", input.Kind)
		}
		v := reflect.New(targetType).Elem()
		v.SetString(input.String)
		return v, nil

	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		if input.Kind != ValueS64 {
			return reflect.Value{}, fmt.Errorf("expected int64, got %v", input.Kind)
		}
		if reflect.Zero(targetType).OverflowInt(input.S64) {
			return reflect.Value{}, fmt.Errorf("integer %d overflows %s", input.S64, targetType)
		}
		v := reflect.New(targetType).Elem()
		v.SetInt(input.S64)
		return v, nil

	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64, reflect.Uintptr:
		if input.Kind != ValueS64 {
			return reflect.Value{}, fmt.Errorf("expected int64 for unsigned target, got %v", input.Kind)
		}
		if input.S64 < 0 {
			return reflect.Value{}, fmt.Errorf("negative integer %d cannot be assigned to %s", input.S64, targetType)
		}
		u := uint64(input.S64)
		if reflect.Zero(targetType).OverflowUint(u) {
			return reflect.Value{}, fmt.Errorf("unsigned integer %d overflows %s", u, targetType)
		}
		v := reflect.New(targetType).Elem()
		v.SetUint(u)
		return v, nil

	case reflect.Float32, reflect.Float64:
		v := reflect.New(targetType).Elem()
		switch input.Kind {
		case ValueF64:
			if targetType.Kind() == reflect.Float32 && (input.F64 > math.MaxFloat32 || input.F64 < -math.MaxFloat32) {
				return reflect.Value{}, fmt.Errorf("float %f overflows %s", input.F64, targetType)
			}
			v.SetFloat(input.F64)
			return v, nil
		case ValueS64:
			f := float64(input.S64)
			if targetType.Kind() == reflect.Float32 && (f > math.MaxFloat32 || f < -math.MaxFloat32) {
				return reflect.Value{}, fmt.Errorf("float %f overflows %s", f, targetType)
			}
			v.SetFloat(f)
			return v, nil
		default:
			return reflect.Value{}, fmt.Errorf("expected float64 or int64, got %v", input.Kind)
		}

	case reflect.Slice:
		if input.Kind != ValueList {
			return reflect.Value{}, fmt.Errorf("expected list, got %v", input.Kind)
		}
		result := reflect.MakeSlice(targetType, len(input.List), len(input.List))
		for i, item := range input.List {
			decoded, err := valueToReflect(item, targetType.Elem())
			if err != nil {
				return reflect.Value{}, fmt.Errorf("index %d: %w", i, err)
			}
			result.Index(i).Set(decoded)
		}
		return result, nil

	case reflect.Array:
		if input.Kind != ValueList {
			return reflect.Value{}, fmt.Errorf("expected list, got %v", input.Kind)
		}
		if len(input.List) != targetType.Len() {
			return reflect.Value{}, fmt.Errorf("array length mismatch: got %d want %d", len(input.List), targetType.Len())
		}
		result := reflect.New(targetType).Elem()
		for i, item := range input.List {
			decoded, err := valueToReflect(item, targetType.Elem())
			if err != nil {
				return reflect.Value{}, fmt.Errorf("index %d: %w", i, err)
			}
			result.Index(i).Set(decoded)
		}
		return result, nil

	case reflect.Map:
		if targetType.Key().Kind() != reflect.String {
			return reflect.Value{}, fmt.Errorf("unsupported map key type %s", targetType.Key())
		}
		if input.Kind != ValueObject {
			return reflect.Value{}, fmt.Errorf("expected object, got %v", input.Kind)
		}
		result := reflect.MakeMapWithSize(targetType, len(input.Object))
		for k, item := range input.Object {
			decoded, err := valueToReflect(item, targetType.Elem())
			if err != nil {
				return reflect.Value{}, fmt.Errorf("map key %q: %w", k, err)
			}
			result.SetMapIndex(reflect.ValueOf(k), decoded)
		}
		return result, nil

	case reflect.Struct:
		if input.Kind != ValueObject {
			return reflect.Value{}, fmt.Errorf("expected object, got %v", input.Kind)
		}
		result := reflect.New(targetType).Elem()
		if err := populateStructFromObjectModel(input.Object, result); err != nil {
			return reflect.Value{}, err
		}
		return result, nil

	case reflect.Interface:
		if targetType.NumMethod() != 0 {
			return reflect.Value{}, fmt.Errorf("unsupported interface type %s", targetType)
		}
		return reflect.ValueOf(valueToAny(input)), nil

	default:
		return reflect.Value{}, fmt.Errorf("unsupported target kind %s", targetType.Kind())
	}
}

func valueToAny(input Value) any {
	switch input.Kind {
	case ValueNull:
		return nil
	case ValueBool:
		return input.Bool
	case ValueS64:
		return input.S64
	case ValueF64:
		return input.F64
	case ValueString:
		return input.String
	case ValueList:
		result := make([]any, 0, len(input.List))
		for _, item := range input.List {
			result = append(result, valueToAny(item))
		}
		return result
	case ValueObject:
		result := map[string]any{}
		for k, v := range input.Object {
			result[k] = valueToAny(v)
		}
		return result
	default:
		return nil
	}
}

func isStructType(t reflect.Type) bool {
	for t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	return t.Kind() == reflect.Struct
}

func ensureStructValue(v reflect.Value) (reflect.Value, error) {
	for v.Kind() == reflect.Pointer {
		if v.IsNil() {
			v.Set(reflect.New(v.Type().Elem()))
		}
		v = v.Elem()
	}
	if v.Kind() != reflect.Struct {
		return reflect.Value{}, fmt.Errorf("expected struct target, got %s", v.Kind())
	}
	return v, nil
}

func modelHasPrefix(input map[string]Value, prefix string) bool {
	prefixWithDot := prefix + "."
	for k := range input {
		if strings.HasPrefix(k, prefixWithDot) {
			return true
		}
	}
	return false
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
