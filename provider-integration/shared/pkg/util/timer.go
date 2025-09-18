package util

import "time"

type Timer struct {
	mark time.Time
}

func NewTimer() *Timer {
	return &Timer{
		mark: time.Now(),
	}
}

func (t *Timer) Mark() time.Duration {
	newMark := time.Now()
	result := newMark.Sub(t.mark)
	t.mark = newMark
	return result
}
