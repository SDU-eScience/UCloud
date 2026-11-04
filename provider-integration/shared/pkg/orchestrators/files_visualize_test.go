package orchestrators

import (
	"encoding/json"
	"testing"
)

func TestFilesVisualizePreservesMissingIndexingState(t *testing.T) {
	var response FilesVisualizeResponse
	err := json.Unmarshal([]byte(`{"entries":[],"lastUpdatedAt":null,"fileCount":0,"directoryCount":0,"complete":false}`), &response)
	if err != nil {
		t.Fatal(err)
	}
	if response.Indexing.Present {
		t.Fatal("legacy provider response unexpectedly has an indexing state")
	}
	data, err := json.Marshal(response)
	if err != nil {
		t.Fatal(err)
	}
	var payload map[string]json.RawMessage
	err = json.Unmarshal(data, &payload)
	if err != nil {
		t.Fatal(err)
	}
	if string(payload["indexing"]) != "null" {
		t.Fatalf("legacy indexing state was not preserved: %s", data)
	}
}
