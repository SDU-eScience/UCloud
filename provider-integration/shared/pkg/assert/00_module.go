package assert

import (
	"fmt"
	"reflect"
	"testing"
	"strings"
)

// message formats the optional msgAndArgs into a single string
func message(msgAndArgs ...any) string {
	if len(msgAndArgs) == 0 {
		return ""
	}
	if format, ok := msgAndArgs[0].(string); ok {
		if len(msgAndArgs) > 1 {
			return fmt.Sprintf(format, msgAndArgs[1:]...)
		}
		return format
	}

	b := strings.Builder{}
	for _, item := range msgAndArgs {
		b.WriteString(fmt.Sprint(item))
		b.WriteString(" ")
	}

	return b.String()
}

// Equal fails the test if expected and actual are not deeply equal.
// It returns true when they are equal so that it can be used in combination with
// logical expressions within a test.
func Equal[T comparable](t *testing.T, expected, actual T, msgAndArgs ...any) bool {
	t.Helper()
	if reflect.DeepEqual(expected, actual) {
		return true
	}
	t.Errorf("assert: equal failed – expected %#v, got %#v. %s", expected, actual, message(msgAndArgs...))
	return false
}

// NotEqual fails the test if expected and actual are deeply equal.
func NotEqual[T comparable](t *testing.T, expected, actual T, msgAndArgs ...any) bool {
	t.Helper()
	if !reflect.DeepEqual(expected, actual) {
		return true
	}
	t.Errorf("assert: not equal failed – both are %#v. %s", actual, message(msgAndArgs...))
	return false
}

// True fails the test if actual is not a boolean true.
func True(t *testing.T, actual any, msgAndArgs ...any) bool {
	t.Helper()
	if v, ok := actual.(bool); ok && v {
		return true
	}
	t.Errorf("assert failed: %s", message(msgAndArgs...))
	return false
}

// False fails the test if actual is not a boolean false.
func False(t *testing.T, actual any, msgAndArgs ...any) bool {
	t.Helper()
	if v, ok := actual.(bool); ok && !v {
		return true
	}
	t.Errorf("assert: false failed – expected false, got %#v. %s", actual, message(msgAndArgs...))
	return false
}

// isNil is a helper that determines whether the provided value is nil, taking into
// account typed nils like (*T)(nil) or interface{}(nil).
func isNil(i any) bool {
	if i == nil {
		return true
	}
	v := reflect.ValueOf(i)
	switch v.Kind() {
	case reflect.Chan, reflect.Func, reflect.Interface, reflect.Map, reflect.Pointer, reflect.Slice:
		return v.IsNil()
	}
	return false
}

// Nil fails the test if actual is not nil.
func Nil(t *testing.T, actual any, msgAndArgs ...any) bool {
	t.Helper()
	if isNil(actual) {
		return true
	}
	t.Errorf("assert: nil failed – expected nil, got %#v. %s", actual, message(msgAndArgs...))
	return false
}

// NotNil fails the test if actual is nil.
func NotNil(t *testing.T, actual any, msgAndArgs ...any) bool {
	t.Helper()
	if !isNil(actual) {
		return true
	}
	t.Errorf("assert: not nil failed – expected non-nil. %s", message(msgAndArgs...))
	return false
}
