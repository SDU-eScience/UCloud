package ucx

import "testing"

func TestStructToModelSerializesScalarsAndTags(t *testing.T) {
	type model struct {
		JobName string
		CPU     int64
		Notify  bool
		Hidden  string `ucx:"-"`
		Alias   string `ucx:"custom.key"`
	}

	input := model{
		JobName: "demo",
		CPU:     4,
		Notify:  true,
		Hidden:  "skip",
		Alias:   "value",
	}

	got, err := StructToModel(input)
	if err != nil {
		t.Fatalf("StructToModel returned error: %v", err)
	}

	want := map[string]Value{
		"jobName":    VString("demo"),
		"cpu":        VS64(4),
		"notify":     VBool(true),
		"custom.key": VString("value"),
	}

	if len(got) != len(want) {
		t.Fatalf("unexpected key count: got %d want %d", len(got), len(want))
	}

	for key, wantVal := range want {
		gotVal, ok := got[key]
		if !ok {
			t.Fatalf("missing key: %s", key)
		}
		if !ValuesEqual(gotVal, wantVal) {
			t.Fatalf("unexpected value for key %s: got %#v want %#v", key, gotVal, wantVal)
		}
	}
}

func TestStructToModelFlattensStructsAndKeepsMapsAsObjects(t *testing.T) {
	type nested struct {
		Field string
	}

	type model struct {
		Errors map[string]string
		Nested nested
	}

	input := model{
		Errors: map[string]string{
			"jobName": "invalid",
			"cpu":     "range",
		},
		Nested: nested{Field: "value"},
	}

	got, err := StructToModel(input)
	if err != nil {
		t.Fatalf("StructToModel returned error: %v", err)
	}

	want := map[string]Value{
		"errors": VObject(map[string]Value{
			"jobName": VString("invalid"),
			"cpu":     VString("range"),
		}),
		"nested.field": VString("value"),
	}

	if len(got) != len(want) {
		t.Fatalf("unexpected key count: got %d want %d", len(got), len(want))
	}

	for key, wantVal := range want {
		gotVal, ok := got[key]
		if !ok {
			t.Fatalf("missing key: %s", key)
		}
		if !ValuesEqual(gotVal, wantVal) {
			t.Fatalf("unexpected value for key %s: got %#v want %#v", key, gotVal, wantVal)
		}
	}
}

func TestStructToModelSerializesListsOfStructsAsObjects(t *testing.T) {
	type todoItem struct {
		Id   string
		Text string
	}

	type model struct {
		Todos []todoItem
	}

	input := model{
		Todos: []todoItem{{Id: "1", Text: "first"}},
	}

	got, err := StructToModel(input)
	if err != nil {
		t.Fatalf("StructToModel returned error: %v", err)
	}

	todos, ok := got["todos"]
	if !ok {
		t.Fatalf("missing key: todos")
	}
	if todos.Kind != ValueList {
		t.Fatalf("todos kind: got %v want %v", todos.Kind, ValueList)
	}
	if len(todos.List) != 1 {
		t.Fatalf("todos length: got %d want 1", len(todos.List))
	}

	first := todos.List[0]
	if first.Kind != ValueObject {
		t.Fatalf("list item kind: got %v want %v", first.Kind, ValueObject)
	}
	if !ValuesEqual(first.Object["id"], VString("1")) {
		t.Fatalf("unexpected id value: %#v", first.Object["id"])
	}
	if !ValuesEqual(first.Object["text"], VString("first")) {
		t.Fatalf("unexpected text value: %#v", first.Object["text"])
	}
}
