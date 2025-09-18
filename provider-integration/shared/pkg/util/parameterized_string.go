package util

import (
	"fmt"
	"strings"
)

func InjectParametersIntoString(pattern string, parameters map[string]string) string {
	result := pattern
	for key, value := range parameters {
		result = strings.ReplaceAll(result, fmt.Sprintf("#{%v}", key), fmt.Sprint(value))
	}
	return result
}
