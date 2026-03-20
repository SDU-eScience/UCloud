package ucx

import (
	"testing"
)

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

func TestModelToStructDeserializesScalarsAndNestedFields(t *testing.T) {
	type nested struct {
		Field string
	}

	type model struct {
		JobName string
		CPU     int64
		Notify  bool
		Nested  nested
		Alias   string `ucx:"custom.key"`
	}

	input := map[string]Value{
		"jobName":      VString("demo"),
		"cpu":          VS64(8),
		"notify":       VBool(true),
		"nested.field": VString("value"),
		"custom.key":   VString("alias"),
	}

	var out model
	if err := ModelToStruct(input, &out); err != nil {
		t.Fatalf("ModelToStruct returned error: %v", err)
	}

	if out.JobName != "demo" {
		t.Fatalf("unexpected jobName: got %q want %q", out.JobName, "demo")
	}
	if out.CPU != 8 {
		t.Fatalf("unexpected cpu: got %d want %d", out.CPU, 8)
	}
	if !out.Notify {
		t.Fatalf("unexpected notify: got %v want true", out.Notify)
	}
	if out.Nested.Field != "value" {
		t.Fatalf("unexpected nested.field: got %q want %q", out.Nested.Field, "value")
	}
	if out.Alias != "alias" {
		t.Fatalf("unexpected alias: got %q want %q", out.Alias, "alias")
	}
}

func TestModelToStructDeserializesContainers(t *testing.T) {
	type todoItem struct {
		Id   string
		Text string
	}

	type model struct {
		Errors map[string]string
		Todos  []todoItem
	}

	input := map[string]Value{
		"errors": VObject(map[string]Value{
			"jobName": VString("invalid"),
			"cpu":     VString("range"),
		}),
		"todos": VList([]Value{
			VObject(map[string]Value{
				"id":   VString("1"),
				"text": VString("first"),
			}),
		}),
	}

	var out model
	if err := ModelToStruct(input, &out); err != nil {
		t.Fatalf("ModelToStruct returned error: %v", err)
	}

	if got := out.Errors["jobName"]; got != "invalid" {
		t.Fatalf("unexpected errors.jobName: got %q want %q", got, "invalid")
	}
	if got := out.Errors["cpu"]; got != "range" {
		t.Fatalf("unexpected errors.cpu: got %q want %q", got, "range")
	}
	if len(out.Todos) != 1 {
		t.Fatalf("unexpected todos length: got %d want %d", len(out.Todos), 1)
	}
	if out.Todos[0].Id != "1" {
		t.Fatalf("unexpected todo id: got %q want %q", out.Todos[0].Id, "1")
	}
	if out.Todos[0].Text != "first" {
		t.Fatalf("unexpected todo text: got %q want %q", out.Todos[0].Text, "first")
	}
}
