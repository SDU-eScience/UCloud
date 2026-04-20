package filesystem

import (
	"path/filepath"
	"strings"
	"unicode"
)

// MountPointSanitize turns a user-visible name into a filesystem-safe path segment.
func MountPointSanitize(str string) string {
	str = strings.ToLower(strings.TrimSpace(str))

	var builder strings.Builder
	lastDash := false
	for _, r := range str {
		switch {
		case unicode.IsLetter(r) || unicode.IsDigit(r):
			builder.WriteRune(unicode.ToLower(r))
			lastDash = false

		case r == '-' || r == '_' || r == '.':
			builder.WriteRune(r)
			lastDash = false

		case unicode.IsSpace(r):
			if !lastDash {
				builder.WriteRune('-')
				lastDash = true
			}
		}
	}

	result := strings.Trim(builder.String(), "-._")
	if result == "" {
		return "unnamed"
	}

	return result
}

func BrokerWorkspaceMountRoot(projectTitle string, username string, hasProject bool) string {
	if hasProject {
		return filepath.Join("/ucloud", "projects", MountPointSanitize(projectTitle))
	}

	return filepath.Join("/ucloud", "home", MountPointSanitize(username))
}
