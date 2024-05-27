package util

import (
    "encoding/json"
    "fmt"
    "ucloud.dk/pkg/log"
)

type OptString = Option[string]
type OptInteger = Option[int]

type Option[T any] struct {
    value   T
    present bool
}

func OptValue[T any](value T) Option[T] {
    return Option[T]{value: value, present: true}
}

func OptNone[T any]() Option[T] {
    return Option[T]{present: false}
}

func (s *Option[T]) Set(v T) {
    s.value = v
    s.present = true
}

func (s *Option[T]) Get() T {
    return s.value
}

func (s *Option[T]) Clear() {
    var empty T
    s.value = empty
    s.present = false
}

func (s *Option[T]) IsSet() bool {
    return s.present
}

func (s *Option[T]) IsEmpty() bool {
    return !s.present
}

func (s *Option[T]) String() string {
    return fmt.Sprint(s.value)
}

func (s Option[T]) MarshalJSON() ([]byte, error) {
    log.Info("Marshal option %v %v", s.value, s.present)
    if s.present {
        return json.Marshal(s.value)
    } else {
        return []byte("null"), nil
    }
}

func (s Option[T]) UnmarshalJSON(data []byte) error {
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
