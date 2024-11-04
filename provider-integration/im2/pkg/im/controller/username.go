package controller

import (
	"encoding/base64"
	"net/http"
)

func GetUCloudUsername(r *http.Request) string {
	base64Encoded := r.Header.Get("ucloud-username")
	if len(base64Encoded) == 0 {
		return "_guest" // Not expected to happen for UCloud authenticated requests
	}

	bytes, err := base64.StdEncoding.DecodeString(base64Encoded)
	if err != nil {
		return "_guest"
	}

	return string(bytes)
}
