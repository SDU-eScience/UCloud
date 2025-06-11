package accounting

import (
	"net/http"
	"regexp"
	"strings"
	"ucloud.dk/shared/pkg/util"
)

var (
	// allowed second-segment university codes
	deicUniSet = map[string]struct{}{
		"ku":  {},
		"dtu": {},
		"au":  {},
		"sdu": {},
		"aau": {},
		"ruc": {},
		"itu": {},
		"cbs": {},
	}

	deicAllocRegex   = regexp.MustCompile(`^[LNSI][1-5]$`)
	deicNumericRegex = regexp.MustCompile(`^\d+$`)
)

func checkDeicReferenceFormat(id string) *util.HttpError {
	if !strings.HasPrefix(strings.ToLower(id), "deic-") {
		return nil
	}

	const errorMessage = "Error in DeiC reference id:"

	parts := strings.Split(id, "-")
	switch {
	case len(parts) != 4:
		return util.HttpErr(http.StatusBadRequest, errorMessage+
			" It seems like you are not following request format. DeiC-XX-YY-NUMBER")

	case !isAllowedUni(parts[1]):
		return util.HttpErr(http.StatusBadRequest, errorMessage+" Could not recognize university.")

	case len(parts[2]) != 2:
		return util.HttpErr(http.StatusBadRequest, errorMessage+
			" Allocation category uses the wrong format.")

	case !deicAllocRegex.MatchString(parts[2]):
		return util.HttpErr(http.StatusBadRequest, errorMessage+
			" Allocation category has wrong format.")

	case !deicNumericRegex.MatchString(parts[3]):
		return util.HttpErr(http.StatusBadRequest, errorMessage+
			" Only supports numeric local ids.")
	}

	return nil
}

func isAllowedUni(code string) bool {
	_, ok := deicUniSet[strings.ToLower(code)]
	return ok
}
