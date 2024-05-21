package safe

import "fmt"

type String = safe[string]
type Integer = safe[int]

type safe[T string | int] struct {
	value   T
	present bool
}

func (s *safe[T]) Set(v T) {
	s.value = v
	s.present = true
}

func (s *safe[T]) Get() T {
	return s.value
}

func (s *safe[T]) Clear() {
	var empty T
	s.value = empty
	s.present = false
}

func (s *safe[T]) Present() bool {
	return s.present
}

func (s *safe[T]) String() string {
	return fmt.Sprint(s.value)
}
