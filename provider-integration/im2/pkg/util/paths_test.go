package util

import (
	"fmt"
	"testing"
)

func TestName(t *testing.T) {
	fmt.Printf("%v", Components("/280"))
	if len(Components("/270")) != 1 {
		t.Errorf("Expected 1 component from /270")
	}

	if len(Components("/270/")) != 1 {
		t.Errorf("Expected 1 component from /270/")
	}

}
