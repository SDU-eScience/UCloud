package util

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"gopkg.in/yaml.v3"
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

func (s *Option[T]) Sql() sql.Null[T] {
	if s.Present {
		return sql.Null[T]{Valid: true, V: s.Value}
	} else {
		return sql.Null[T]{Valid: false}
	}
}

func (s Option[T]) GetOrDefault(t T) T {
	if s.Present {
		return s.Value
	}
	return t
}

func (s *Option[T]) Get() T {
	return s.Value
}

func (s Option[T]) GetPtrOrNil() *T {
	if !s.Present {
		return nil
	}
	return &s.Value
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

func (s *Option[T]) UnmarshalJSON(data []byte) error {
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

func (s Option[T]) MarshalYAML() (any, error) {
	if s.Present {
		return s.Value, nil
	}
	return nil, nil
}

func (s *Option[T]) UnmarshalYAML(node *yaml.Node) error {
	if node.Tag == "!!null" {
		s.Present = false
		var zero T
		s.Value = zero
		return nil
	}

	var v T
	if err := node.Decode(&v); err != nil {
		return err
	}
	s.Value = v
	s.Present = true
	return nil
}

func OptStringIfNotEmpty(value string) Option[string] {
	if value == "" {
		return OptNone[string]()
	} else {
		return OptValue[string](value)
	}
}

func OptMapGet[K comparable, V any](value map[K]V, key K) Option[V] {
	if value == nil {
		return OptNone[V]()
	} else {
		result, ok := value[key]
		if ok {
			return OptValue(result)
		} else {
			return OptNone[V]()
		}
	}
}

func OptSqlStringIfNotEmpty(value string) sql.NullString {
	if value == "" {
		return sql.NullString{Valid: false}
	} else {
		return sql.NullString{Valid: true, String: value}
	}
}

func SqlNullStringToOpt(value sql.NullString) Option[string] {
	if value.Valid {
		return OptValue(value.String)
	} else {
		return OptNone[string]()
	}
}

func SqlNullToOpt[T any](value sql.Null[T]) Option[T] {
	if value.Valid {
		return OptValue(value.V)
	} else {
		return OptNone[T]()
	}
}

func OptDefaultOrMap[T any, R any](opt Option[T], defaultValue R, mapper func(val T) R) R {
	if opt.Present {
		return mapper(opt.Value)
	} else {
		return defaultValue
	}
}

func OptMap[A any, B any](opt Option[A], mapper func(value A) B) Option[B] {
	if opt.Present {
		return OptValue[B](mapper(opt.Value))
	} else {
		return OptNone[B]()
	}
}
