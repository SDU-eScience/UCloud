package ucx

import "testing"

func TestNormalizeUiTreeAssignsImplicitIdsForNonInteractive(t *testing.T) {
	root := Box().Children(
		Text("hello"),
		Flex(FlexProps{Direction: "column", Gap: 8}).Children(
			DividerNode(),
		),
	)

	normalized := NormalizeUiTree(root)

	if normalized.Id == "" {
		t.Fatal("expected implicit id on root")
	}
	if normalized.ChildNodes[0].Id == "" {
		t.Fatal("expected implicit id on first child")
	}
	if normalized.ChildNodes[1].ChildNodes[0].Id == "" {
		t.Fatal("expected implicit id on nested child")
	}

	again := NormalizeUiTree(root)
	if normalized.Id != again.Id {
		t.Fatalf("expected deterministic implicit root id, got %q and %q", normalized.Id, again.Id)
	}
}

func TestNormalizeUiTreePanicsForInteractiveWithoutId(t *testing.T) {
	root := BoxEx("root").Children(
		UiNode{Component: "button"},
	)

	defer func() {
		if recover() == nil {
			t.Fatal("expected panic for interactive component without explicit id")
		}
	}()

	_ = NormalizeUiTree(root)
}
