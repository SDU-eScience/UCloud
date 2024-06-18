package util

import (
	"encoding/json"
	"fmt"
)

type OptString = Option[string]
type OptInteger = Option[int]

type Option[T any] struct {
	// NOTE(Dan): We need to export these for client.StructToParameters to work

	Value   T
	Present bool
}

func OptValue[T any](value T) Option[T] {
	return Option[T]{Value: value, Present: true}
}

func OptNone[T any]() Option[T] {
	return Option[T]{Present: false}
}

func (s *Option[T]) Set(v T) {
	s.Value = v
	s.Present = true
}

func (s *Option[T]) Get() T {
	return s.Value
}

func (s *Option[T]) Clear() {
	var empty T
	s.Value = empty
	s.Present = false
}

func (s *Option[T]) IsSet() bool {
	return s.Present
}

func (s *Option[T]) IsEmpty() bool {
	return !s.Present
}

func (s *Option[T]) String() string {
	return fmt.Sprint(s.Value)
}

func (s Option[T]) MarshalJSON() ([]byte, error) {
	if s.Present {
		return json.Marshal(s.Value)
	} else {
		return []byte("null"), nil
	}
}

func (s Option[T]) UnmarshalJSON(data []byte) error {
	if string(data) == "null" {
		s.Present = false
		return nil
	}

	err := json.Unmarshal(data, &s.Value)
	if err != nil {
		return err
	}

	s.Present = true
	return nil
}
