package util

import (
	"fmt"
	"testing"
	"time"
)

func TestExponentialBackoff(t *testing.T) {
	for i := 0; i < 100; i++ {
		fmt.Printf("%v\n", ExponentialBackoffForNetwork(i))
	}
}

func TestExponentialBackoff2(t *testing.T) {
	for i := 0; i < 100; i++ {
		fmt.Printf("%v\n", ExponentialBackoffEx(i, 5*time.Millisecond, 2, 20*time.Second))
	}
}
