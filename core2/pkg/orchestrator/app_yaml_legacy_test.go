package orchestrator

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"gopkg.in/yaml.v3"
	"testing"
	"ucloud.dk/shared/pkg/log"
)

//go:embed test-yaml/legacy_mongodb.yaml
var mongodbData []byte

//go:embed test-yaml/legacy_redis.yaml
var redisData []byte

//go:embed test-yaml/legacy_chatui.yaml
var chatUiData []byte

func TestUnmarshal(t *testing.T) {
	var param A1Integer
	y := `type: integer
title: "MongoDB port"
defaultValue:
    value: 42
description: Port to connect to
optional: true`

	err := yaml.Unmarshal([]byte(y), &param)
	if err != nil {
		t.Fatal(err)
	}
}

func testApp(t *testing.T, input []byte) {
	var app A1Yaml
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

func TestLegacyMongodbApplication(t *testing.T) {
	testApp(t, mongodbData)
}

func TestLegacyRedisApplication(t *testing.T) {
	testApp(t, redisData)
}

func TestLegacyChatUiApplication(t *testing.T) {
	testApp(t, chatUiData)
}
