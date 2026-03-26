package util

import "testing"

func TestMapMergeOverrideOrder(t *testing.T) {
	merged := MapMerge(
		map[string]int{"a": 1, "b": 2},
		map[string]int{"b": 20, "c": 3},
		map[string]int{"c": 30},
	)

	if len(merged) != 3 {
		t.Fatalf("expected 3 keys, got %d", len(merged))
	}

	if merged["a"] != 1 {
		t.Fatalf("expected a=1, got %d", merged["a"])
	}

	if merged["b"] != 20 {
		t.Fatalf("expected b=20, got %d", merged["b"])
	}

	if merged["c"] != 30 {
		t.Fatalf("expected c=30, got %d", merged["c"])
	}
}

func TestMapMergeAlwaysReturnsMap(t *testing.T) {
	merged := MapMerge[string, int]()
	if merged == nil {
		t.Fatal("expected non-nil map")
	}

	mergedWithNil := MapMerge(
		map[string]int{"a": 1},
		nil,
		map[string]int{"b": 2},
	)

	if mergedWithNil == nil {
		t.Fatal("expected non-nil map")
	}

	if len(mergedWithNil) != 2 {
		t.Fatalf("expected 2 keys, got %d", len(mergedWithNil))
	}
}
