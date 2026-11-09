package util

import (
	"encoding/json"
	"reflect"

	"ucloud.dk/shared/pkg/log"
)

// SanitizeMapForSerialization recursively removes non-serializable values from a map[string]any
func SanitizeMapForSerialization(input map[string]any) map[string]any {
	safeMap := make(map[string]any)
	for key, value := range input {
		if IsSerializable(value) {
			switch v := value.(type) {
			case map[string]any:
				safeMap[key] = SanitizeMapForSerialization(v)
			default:
				safeMap[key] = v
			}
		}
	}
	return safeMap
}

// IsSerializable checks if a value is serializable by JSON or YAML
func IsSerializable(value any) bool {
	v := reflect.ValueOf(value)
	switch v.Kind() {
	case reflect.Func, reflect.Chan, reflect.Complex64, reflect.Complex128, reflect.Invalid:
		return false
	default:
		return true
	}
}

func MustJsonMarshal(value any) []byte {
	data, err := json.Marshal(value)
	if err != nil {
		log.Fatal("util: could not encode %T as JSON: %s", value, err)
	}
	return data
}

func MustJsonMarshalIndent(value any, prefix string, indent string) []byte {
	data, err := json.MarshalIndent(value, prefix, indent)
	if err != nil {
		log.Fatal("util: could not encode %T as JSON: %s", value, err)
	}
	return data
}
