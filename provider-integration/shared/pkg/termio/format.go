package termio

import (
	"strconv"
	"strings"
)

func EscapeName(value string) string {
	quoted := strconv.Quote(value)
	return quoted[1 : len(quoted)-1]
}

func QuoteName(value string) string {
	return "'" + strings.ReplaceAll(EscapeName(value), "'", "\\'") + "'"
}

func QuoteNames(values []string) string {
	names := make([]string, 0, len(values))
	for _, value := range values {
		names = append(names, QuoteName(value))
	}
	return strings.Join(names, ", ")
}
