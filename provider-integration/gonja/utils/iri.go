package utils

import (
	"bytes"
	"net/url"
	"strings"
)

const filterIRIChars = "/#%[]=:;$&()+,!?*@'~"

func IRIEncode(in string) string {
	var b bytes.Buffer

	for _, r := range in {
		if strings.IndexRune(filterIRIChars, r) >= 0 {
			b.WriteRune(r)
		} else {
			b.WriteString(url.QueryEscape(string(r)))
		}
	}

	return b.String()
}
