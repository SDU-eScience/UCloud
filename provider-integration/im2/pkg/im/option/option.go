package option

import (
	"encoding/json"
	"fmt"
)

type String = option[string]
type Integer = option[int]

type option[T any] struct {
	value   T
	present bool
}

func Set[T any](value T) option[T] {
	return option[T]{value: value, present: true}
}

func None[T any]() option[T] {
	return option[T]{present: false}
}

func (s *option[T]) Set(v T) {
	s.value = v
	s.present = true
}

func (s *option[T]) Get() T {
	return s.value
}

func (s *option[T]) Clear() {
	var empty T
	s.value = empty
	s.present = false
}

func (s *option[T]) IsSet() bool {
	return s.present
}

func (s *option[T]) IsEmpty() bool {
	return !s.present
}

func (s *option[T]) String() string {
	return fmt.Sprint(s.value)
}

func (s *option[T]) MarshalJSON() ([]byte, error) {
	if s.present {
		return json.Marshal(s.value)
	} else {
		return []byte("null"), nil
	}
}

func (s *option[T]) UnmarshalJSON(data []byte) error {
	if string(data) == "null" {
		s.present = false
		return nil
	}

	err := json.Unmarshal(data, &s.value)
	if err != nil {
		return err
	}

	s.present = true
	return nil
}
