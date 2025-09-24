package util

import "encoding/base64"

func Base64DecodeToString(input string) string {
	bytes, err := base64.StdEncoding.DecodeString(input)
	if err != nil {
		return ""
	} else {
		return string(bytes)
	}
}
