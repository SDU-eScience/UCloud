package util

import (
	"strings"
	"unicode"
)

func ToSnakeCase(str string) string {
	var result []rune
	for i, r := range str {
		if unicode.IsUpper(r) {
			if r == 'C' && i > 0 {
				// UCloud exception allows us to transform "UCloudProperty" to "ucloud_property" which is what we want.
				if !strings.HasPrefix(str[i-1:], "UCloud") {
					result = append(result, '_')
				}
			} else if i > 0 {
				result = append(result, '_')
			}
			r = unicode.ToLower(r)
		}
		result = append(result, r)
	}
	return string(result)
}
