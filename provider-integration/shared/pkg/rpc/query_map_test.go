package rpc

import (
	"net/url"
	"testing"
)

func TestStructToParametersEncodesStringMap(t *testing.T) {
	type Req struct {
		FilterLabels map[string]string
	}

	req := Req{FilterLabels: map[string]string{"ucloud.dk/stackname": "alpha", "ucloud.dk/stackinstance": "1"}}
	params := StructToParameters(req)

	decoded := map[string]string{}
	for i := 0; i+1 < len(params); i += 2 {
		decoded[params[i]] = params[i+1]
	}

	if decoded["filterLabels.ucloud.dk/stackname"] != "alpha" {
		t.Fatalf("missing stackname in query parameters: %#v", decoded)
	}
	if decoded["filterLabels.ucloud.dk/stackinstance"] != "1" {
		t.Fatalf("missing stackinstance in query parameters: %#v", decoded)
	}
}

func TestParametersToStructDecodesStringMap(t *testing.T) {
	type Req struct {
		FilterLabels map[string]string
	}

	var req Req
	err := ParametersToStruct(url.Values{
		"filterLabels.ucloud.dk/stackname":     []string{"alpha"},
		"filterLabels.ucloud.dk/stackinstance": []string{"2"},
	}, &req)
	if err != nil {
		t.Fatalf("ParametersToStruct returned error: %v", err)
	}

	if req.FilterLabels["ucloud.dk/stackname"] != "alpha" {
		t.Fatalf("unexpected stackname: %#v", req.FilterLabels)
	}
	if req.FilterLabels["ucloud.dk/stackinstance"] != "2" {
		t.Fatalf("unexpected stackinstance: %#v", req.FilterLabels)
	}
}
