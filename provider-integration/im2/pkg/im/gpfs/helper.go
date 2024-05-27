package gpfs

import (
	"regexp"
)

func gpfsValidateName(s string) bool {
	re := regexp.MustCompile(`^([a-z][a-z0-9_-]+)$`)
	return re.MatchString(s)
}
