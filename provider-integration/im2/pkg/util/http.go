package util

type HttpError struct {
	StatusCode   int
	Why          string
	ErrorCode    string
	DetailedCode int
}

func (e *HttpError) Error() string {
	return e.Why
}
