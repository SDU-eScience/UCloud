package slurm

import (
	"regexp"
)

func validateName(s string) bool {
	re := regexp.MustCompile(`^([a-z][a-z0-9_-]+)$`)
	return re.MatchString(s)
}
