package freeipa

import (
	"fmt"
	"regexp"
	"strings"
)

func ipa_validate_name(s string) bool {
	re := regexp.MustCompile(`^([a-z][a-z0-9_-]+)$`)
	return re.MatchString(s)
}

func ipa_validate_mail(s string) bool {
	re := regexp.MustCompile(`^([^@\s]+)@([^@\s]+)\.([^@\s]+)$`)
	return re.MatchString(s)
}

func ipa_handle_error(rw *ResponseWrapper, method string) error {
	if rw.Error == nil {
		return nil
	}

	// no modifications to be performed
	if rw.Error.Code == 4202 {
		return nil
	}

	// no such entry
	if rw.Error.Code == 4001 {
		if strings.HasSuffix(method, "_del") {
			return nil // do not fail when deleting a non-existing entry
		}
	}

	return fmt.Errorf("ipa error (%d): %s", rw.Error.Code, rw.Error.Message)
}

func ipa_extract_users(r *ResponseGroup) []string {
	var users []string

	re := regexp.MustCompile(`uid=(.*?),`)
	arr := append(r.Member, r.MemberIndirect...)

	for _, v := range arr {
		m := re.FindStringSubmatch(v)
		if len(m) > 0 {
			users = append(users, m[1])
		}
	}

	return users
}
