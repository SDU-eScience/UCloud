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

func TestStructToModelSerializesIntegerKeyMapsAsDecimalStrings(t *testing.T) {
	type model struct {
		ByIndex map[int]string
	}

	input := model{
		ByIndex: map[int]string{
			-2: "neg",
			7:  "pos",
		},
	}

	got, err := StructToModel(input)
	if err != nil {
		t.Fatalf("StructToModel returned error: %v", err)
	}

	byIndex, ok := got["byIndex"]
	if !ok {
		t.Fatalf("missing key: byIndex")
	}
	if byIndex.Kind != ValueObject {
		t.Fatalf("byIndex kind: got %v want %v", byIndex.Kind, ValueObject)
	}

	if !ValuesEqual(byIndex.Object["-2"], VString("neg")) {
		t.Fatalf("unexpected byIndex[-2]: %#v", byIndex.Object["-2"])
	}
	if !ValuesEqual(byIndex.Object["7"], VString("pos")) {
		t.Fatalf("unexpected byIndex[7]: %#v", byIndex.Object["7"])
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

func TestModelToStructDeserializesIntegerKeyMapsFromDecimalStrings(t *testing.T) {
	type model struct {
		ByIndex map[int]string
		ByPort  map[uint16]bool
	}

	input := map[string]Value{
		"byIndex": VObject(map[string]Value{
			"-2": VString("neg"),
			"7":  VString("pos"),
		}),
		"byPort": VObject(map[string]Value{
			"443": VBool(true),
		}),
	}

	var out model
	if err := ModelToStruct(input, &out); err != nil {
		t.Fatalf("ModelToStruct returned error: %v", err)
	}

	if got := out.ByIndex[-2]; got != "neg" {
		t.Fatalf("unexpected byIndex[-2]: got %q want %q", got, "neg")
	}
	if got := out.ByIndex[7]; got != "pos" {
		t.Fatalf("unexpected byIndex[7]: got %q want %q", got, "pos")
	}
	if got := out.ByPort[443]; !got {
		t.Fatalf("unexpected byPort[443]: got %v want true", got)
	}
}

func TestApplyModelInputIsPartialAndLeavesOtherFieldsUntouched(t *testing.T) {
	type model struct {
		JobName string
		CPU     int64
		Hidden  string `ucx:"-"`
	}

	state := model{
		JobName: "existing",
		CPU:     4,
		Hidden:  "secret",
	}

	err := ApplyModelInput(&state, ModelInput{
		Path:  "jobName",
		Value: VString("updated"),
	})
	if err != nil {
		t.Fatalf("ApplyModelInput returned error: %v", err)
	}

	if state.JobName != "updated" {
		t.Fatalf("unexpected jobName: got %q want %q", state.JobName, "updated")
	}
	if state.CPU != 4 {
		t.Fatalf("unexpected cpu mutation: got %d want %d", state.CPU, 4)
	}
	if state.Hidden != "secret" {
		t.Fatalf("unexpected hidden mutation: got %q want %q", state.Hidden, "secret")
	}
}

func TestApplyModelInputDeserializesSliceOfStructs(t *testing.T) {
	type todoItem struct {
		Id   string
		Text string
	}

	type model struct {
		Todos []todoItem
	}

	state := model{}
	err := ApplyModelInput(&state, ModelInput{
		Path: "todos",
		Value: VList([]Value{
			VObject(map[string]Value{"id": VString("1"), "text": VString("first")}),
			VObject(map[string]Value{"id": VString("2"), "text": VString("second")}),
		}),
	})
	if err != nil {
		t.Fatalf("ApplyModelInput returned error: %v", err)
	}

	if len(state.Todos) != 2 {
		t.Fatalf("unexpected todos length: got %d want %d", len(state.Todos), 2)
	}
	if state.Todos[0].Id != "1" || state.Todos[0].Text != "first" {
		t.Fatalf("unexpected first todo: %#v", state.Todos[0])
	}
	if state.Todos[1].Id != "2" || state.Todos[1].Text != "second" {
		t.Fatalf("unexpected second todo: %#v", state.Todos[1])
	}
}

func TestValueMarshalSupportsBarePrimitiveAndArrayRoots(t *testing.T) {
	primitive, err := ValueMarshal("hello")
	if err != nil {
		t.Fatalf("ValueMarshal primitive returned error: %v", err)
	}
	if !ValuesEqual(primitive[""], VString("hello")) {
		t.Fatalf("unexpected primitive payload: %#v", primitive[""])
	}

	array, err := ValueMarshal([2]int{7, 9})
	if err != nil {
		t.Fatalf("ValueMarshal array returned error: %v", err)
	}
	wantArray := VList([]Value{VS64(7), VS64(9)})
	if !ValuesEqual(array[""], wantArray) {
		t.Fatalf("unexpected array payload: got %#v want %#v", array[""], wantArray)
	}
}

func TestValueUnmarshalSupportsBarePrimitiveAndArrayRoots(t *testing.T) {
	var primitive int64
	if err := ValueUnmarshal(map[string]Value{"": VS64(42)}, &primitive); err != nil {
		t.Fatalf("ValueUnmarshal primitive returned error: %v", err)
	}
	if primitive != 42 {
		t.Fatalf("unexpected primitive value: got %d want %d", primitive, 42)
	}

	var array [3]string
	if err := ValueUnmarshal(map[string]Value{"": VList([]Value{VString("a"), VString("b"), VString("c")})}, &array); err != nil {
		t.Fatalf("ValueUnmarshal array returned error: %v", err)
	}
	if array != [3]string{"a", "b", "c"} {
		t.Fatalf("unexpected array value: got %#v", array)
	}
}
