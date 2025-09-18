package orchestrator

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"gopkg.in/yaml.v3"
	"testing"
	"ucloud.dk/shared/pkg/assert"
	"ucloud.dk/shared/pkg/log"
)

//go:embed test-yaml/v2_terminal.yaml
var terminalData []byte

func testApp2(t *testing.T, input []byte) {
	var app A2Yaml
	err := yaml.Unmarshal(input, &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	log.Info("App: %s\n", app.Name)

	napp, herr := app.Normalize()
	if herr != nil {
		t.Fatalf("Invalid app: %s", herr)
	}

	log.Info("Normalized app: %#v", napp)

	data, _ := json.Marshal(napp.Invocation)
	fmt.Printf("%s\n", data)
}

func TestTerminal(t *testing.T) {
	testApp2(t, terminalData)
}

func TestParam2Unmarshal(t *testing.T) {
	p := `type: "Integer"
title: "Title"
description: "Description"
defaultValue: 42
min: 1
max: 100
optional: true
`

	var param A2Parameter
	err := yaml.Unmarshal([]byte(p), &param)
	if err != nil {
		panic(err)
	}

	if assert.NotNil(t, param.Integer) {
		assert.Equal(t, "Title", param.Integer.Title)
		assert.Equal(t, "Description", param.Integer.Description)
		assert.Equal(t, true, param.Integer.Optional)
		assert.Equal(t, true, param.Integer.DefaultValue.Present)
		assert.Equal(t, int64(42), param.Integer.DefaultValue.Value)

		assert.Equal(t, true, param.Integer.Min.Present)
		assert.Equal(t, int64(1), param.Integer.Min.Value)

		assert.Equal(t, true, param.Integer.Max.Present)
		assert.Equal(t, int64(100), param.Integer.Max.Value)
	}
}
