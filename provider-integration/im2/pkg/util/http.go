package util

import (
	"fmt"
	"net/http"
)

type HttpError struct {
	StatusCode   int
	Why          string
	ErrorCode    string
	DetailedCode int
}

func (e *HttpError) Error() string {
	return e.Why
}

func UserHttpError(whyFormat string, args ...any) *HttpError {
	return &HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        fmt.Sprintf(whyFormat, args...),
	}
}

func ServerHttpError(whyFormat string, args ...any) *HttpError {
	return &HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        fmt.Sprintf(whyFormat, args...),
	}
}
