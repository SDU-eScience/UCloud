package util

import (
	"net/http"
	"strings"
	"unicode"
)

type StringValidationFlag uint

const (
	// StringValidationAllowLong allows for strings to be longer than 1024 (limit becomes 1024 * 1024 instead)
	StringValidationAllowLong StringValidationFlag = 1 << iota

	// StringValidationAllowEmpty allows for strings to be empty
	StringValidationAllowEmpty

	// StringValidationAllowMultiline allows strings to contain newlines
	StringValidationAllowMultiline

	// StringValidationRejectSpaces causes spaces to be rejected from the string
	StringValidationRejectSpaces

	// StringValidationRejectSymbols causes non-alphanumeric characters to be rejected
	StringValidationRejectSymbols

	// StringValidationNoTrim turns of automatic trimming of the field
	StringValidationNoTrim
)

func ValidateStringE(input *string, fieldName string, opts StringValidationFlag) *HttpError {
	var e *HttpError = nil
	ValidateString(input, fieldName, opts, &e)
	return e
}

func ValidateString(input *string, fieldName string, opts StringValidationFlag, err **HttpError) {
	if input == nil {
		return
	}
	if err == nil {
		panic("err must not be nil")
	}

	setErr := func(e *HttpError) {
		if *err == nil {
			*err = e
		}
	}

	result := *input

	// Normalization
	// -----------------------------------------------------------------------------------------------------------------

	if opts&StringValidationNoTrim == 0 {
		result = strings.TrimSpace(result)
	}

	*input = result // must be the last line before validation begins

	// Validation
	// -----------------------------------------------------------------------------------------------------------------

	if opts&StringValidationAllowLong == 0 {
		if len(result) > 1024 {
			setErr(HttpErr(http.StatusBadRequest, "%v is too long", fieldName))
			return
		}
	} else {
		if len(result) > 1024*1024 {
			setErr(HttpErr(http.StatusBadRequest, "%v is too long", fieldName))
			return
		}
	}

	if opts&StringValidationAllowEmpty == 0 {
		if len(result) == 0 {
			setErr(HttpErr(http.StatusBadRequest, "%v must not be empty", fieldName))
			return
		}
	}

	if opts&StringValidationAllowMultiline == 0 {
		if strings.ContainsRune(result, '\n') {
			setErr(HttpErr(http.StatusBadRequest, "%v must not contain new-lines", fieldName))
			return
		}
	}

	if opts&StringValidationRejectSpaces != 0 {
		if strings.IndexFunc(result, unicode.IsSpace) >= 0 {
			setErr(HttpErr(http.StatusBadRequest, "%v must not contain any spaces", fieldName))
			return
		}
	}

	if opts&StringValidationRejectSymbols != 0 {
		valid := strings.IndexFunc(result, func(r rune) bool {
			return !unicode.IsLetter(r) && !unicode.IsDigit(r)
		}) < 0

		if !valid {
			setErr(HttpErr(http.StatusBadRequest, "%v must not contain any symbols", fieldName))
			return
		}
	}
}

func ValidateEnum[T comparable](input *T, options []T, fieldName string, err **HttpError) {
	if input == nil {
		return
	}

	if err == nil {
		panic("err must not be nil")
	}

	setErr := func(e *HttpError) {
		if *err == nil {
			*err = e
		}
	}

	_, ok := VerifyEnum(*input, options)
	if !ok {
		setErr(HttpErr(http.StatusBadRequest, "invalid enumeration specified in '%s'", fieldName))
	}
}
