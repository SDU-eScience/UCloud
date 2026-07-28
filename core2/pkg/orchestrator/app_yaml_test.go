package orchestrator

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"gopkg.in/yaml.v3"
	"testing"
	"ucloud.dk/shared/pkg/assert"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
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

func TestInferenceDefaultsToNoneWhenOmitted(t *testing.T) {
	var app A2Yaml
	err := yaml.Unmarshal([]byte(`
name: test-app
version: "1"
software:
  type: Container
  image: ubuntu:latest
invocation: echo hello
`), &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	napp, herr := app.Normalize()
	if herr != nil {
		t.Fatalf("Invalid app: %s", herr)
	}

	assert.Equal(t, false, napp.Invocation.Inference.Present)
}

func TestInferenceMandatory(t *testing.T) {
	var app A2Yaml
	err := yaml.Unmarshal([]byte(`
name: test-app
version: "1"
software:
  type: Container
  image: ubuntu:latest
inference:
  mode: Mandatory
invocation: echo hello
`), &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	napp, herr := app.Normalize()
	if herr != nil {
		t.Fatalf("Invalid app: %s", herr)
	}

	if assert.Equal(t, true, napp.Invocation.Inference.Present) {
		assert.Equal(t, orcapi.InferenceModeMandatory, napp.Invocation.Inference.Value.Mode)
	}
}

func TestUcxExecutableMetadataValidation(t *testing.T) {
	valid := orcapi.ApplicationInvocationDescription{
		Ucx: util.OptValue(orcapi.UcxDescription{
			Executable: util.OptValue(orcapi.UcxExecutableDescription{
				ManifestUrl: "https://provider.example.org/ucloud/ucx/my-app/manifest.json",
				PublicKey:   "ed25519:BASE64_PUBLIC_KEY",
				BinaryName:  "my-ucx-app",
			}),
		}),
	}
	assert.Equal(t, (*util.HttpError)(nil), validateUcxExecutableMetadata(&valid))

	invalid := valid
	invalid.Ucx.Value.Executable.Value.PublicKey = "rsa:BASE64_PUBLIC_KEY"
	if validateUcxExecutableMetadata(&invalid) == nil {
		t.Fatalf("expected invalid UCX executable public key prefix to be rejected")
	}
}

func TestBuiltInUcxExecutableMetadataValidation(t *testing.T) {
	valid := orcapi.ApplicationInvocationDescription{
		Ucx: util.OptValue(orcapi.UcxDescription{
			Executable: util.OptValue(orcapi.UcxExecutableDescription{ManifestUrl: "builtin://ucx-syncthing"}),
		}),
	}
	assert.Equal(t, (*util.HttpError)(nil), validateUcxExecutableMetadata(&valid))

	for _, manifestUrl := range []string{
		"builtin://",
		"builtin://.",
		"builtin://..",
		"builtin://foo/bar",
		"builtin://foo\\bar",
		"builtin://foo?version=1",
		"builtin://foo#fragment",
	} {
		invalid := valid
		invalid.Ucx.Value.Executable.Value.ManifestUrl = manifestUrl
		if validateUcxExecutableMetadata(&invalid) == nil {
			t.Errorf("expected %q to be rejected", manifestUrl)
		}
	}
}

func TestA2UcxExecutableMetadata(t *testing.T) {
	var app A2Yaml
	err := yaml.Unmarshal([]byte(`
name: test-ucx-app
version: "1"
software:
  type: UCX
  image: ucloud/ucx-runner:latest
invocation: echo hello
ucx:
  executable:
    manifestUrl: https://provider.example.org/ucloud/ucx/my-app/manifest.json
    publicKey: ed25519:BASE64_PUBLIC_KEY
    binaryName: my-ucx-app
`), &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	napp, herr := app.Normalize()
	if herr != nil {
		t.Fatalf("Invalid app: %s", herr)
	}

	if assert.Equal(t, true, napp.Invocation.Ucx.Present) &&
		assert.Equal(t, true, napp.Invocation.Ucx.Value.Executable.Present) {
		executable := napp.Invocation.Ucx.Value.Executable.Value
		assert.Equal(t, "https://provider.example.org/ucloud/ucx/my-app/manifest.json", executable.ManifestUrl)
		assert.Equal(t, "ed25519:BASE64_PUBLIC_KEY", executable.PublicKey)
		assert.Equal(t, "my-ucx-app", executable.BinaryName)
	}

	assert.Equal(t, "echo hello", napp.Invocation.Invocation[0].InvocationParameterJinja.Template)
}

func TestA2BuiltInUcxExecutableMetadata(t *testing.T) {
	var app A2Yaml
	err := yaml.Unmarshal([]byte(`
name: test-builtin-app
version: "1"
software:
  type: Container
  image: ubuntu:latest
invocation: echo hello
ucx:
  executable:
    manifestUrl: builtin://ucx-syncthing
`), &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	napp, herr := app.Normalize()
	if herr != nil {
		t.Fatalf("Invalid app: %s", herr)
	}
	executable := napp.Invocation.Ucx.Value.Executable.Value
	assert.Equal(t, "builtin://ucx-syncthing", executable.ManifestUrl)
	assert.Equal(t, "", executable.PublicKey)
	assert.Equal(t, "", executable.BinaryName)
}

func TestA2UcxExecutableMetadataRejectsInvalidPublicKey(t *testing.T) {
	var app A2Yaml
	err := yaml.Unmarshal([]byte(`
name: test-ucx-app
version: "1"
software:
  type: UCX
  image: ucloud/ucx-runner:latest
invocation: echo hello
ucx:
  executable:
    manifestUrl: https://provider.example.org/ucloud/ucx/my-app/manifest.json
    publicKey: rsa:BASE64_PUBLIC_KEY
    binaryName: my-ucx-app
`), &app)
	if err != nil {
		t.Fatalf("Invalid YAML: %s", err)
	}

	if _, herr := app.Normalize(); herr == nil {
		t.Fatalf("expected invalid UCX executable public key prefix to be rejected")
	}
}
