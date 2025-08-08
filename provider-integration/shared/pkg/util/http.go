package util

import (
	"fmt"
	"net/http"
)

type HttpError struct {
	StatusCode   int    `json:"statusCode"`
	Why          string `json:"why"`
	ErrorCode    string `json:"errorCode"`
	DetailedCode int    `json:"detailedCode"`
}

func (e *HttpError) Error() string {
	return fmt.Sprintf("%v %v", e.StatusCode, e.Why)
}

func UserHttpError(whyFormat string, args ...any) *HttpError {
	message := ""
	if len(args) == 0 {
		message = whyFormat
	} else {
		message = fmt.Sprintf(whyFormat, args...)
	}

	return &HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        message,
	}
}

func ServerHttpError(whyFormat string, args ...any) *HttpError {
	message := ""
	if len(args) == 0 {
		message = whyFormat
	} else {
		message = fmt.Sprintf(whyFormat, args...)
	}

	return &HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        message,
	}
}

func HttpErr(statusCode int, whyFormat string, args ...any) *HttpError {
	message := ""
	if len(args) == 0 {
		message = whyFormat
	} else {
		message = fmt.Sprintf(whyFormat, args...)
	}

	return &HttpError{
		StatusCode: statusCode,
		Why:        message,
	}
}

func PaymentError() *HttpError {
	return &HttpError{
		StatusCode: http.StatusPaymentRequired,
		Why:        "Insufficient funds",
	}
}
