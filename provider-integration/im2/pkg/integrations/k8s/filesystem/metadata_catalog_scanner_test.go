package filesystem

import (
	"os"
	"path/filepath"
	"testing"
)

func TestMetadataNearestExistingDirectory(t *testing.T) {
	root := t.TempDir()
	existing := filepath.Join(root, "projects", "alpha")
	if err := os.MkdirAll(existing, 0o700); err != nil {
		t.Fatal(err)
	}

	resolved, ok := metadataNearestExistingDirectory(root, filepath.Join(existing, "missing", "file.dat"))
	if !ok || resolved != existing {
		t.Fatalf("resolved %q, ok=%t; expected %q", resolved, ok, existing)
	}
	resolved, ok = metadataNearestExistingDirectory(root, filepath.Dir(root))
	if !ok || resolved != root {
		t.Fatalf("outside path resolved %q, ok=%t; expected drive root %q", resolved, ok, root)
	}
}

func TestMetadataDirectDriveRootParentIsClamped(t *testing.T) {
	driveRoot := filepath.Join(t.TempDir(), "drive")
	if err := os.MkdirAll(driveRoot, 0o700); err != nil {
		t.Fatal(err)
	}

	scanRoot := metadataClampScanRoot(driveRoot, filepath.Dir(driveRoot))
	resolved, ok := metadataNearestExistingDirectory(driveRoot, scanRoot)
	if !ok || resolved != driveRoot {
		t.Fatalf("resolved %q, ok=%t; expected drive root %q", resolved, ok, driveRoot)
	}
}

func TestMetadataCollapseScanCandidates(t *testing.T) {
	candidates := []metadataScanCandidate{
		{driveID: "d1", ucloudPath: "/d1/projects/alpha", interval: metadataActiveInterval, states: []metadataScanState{{driveID: "d1", ucloudPath: "/d1/projects/alpha"}}},
		{driveID: "d1", ucloudPath: "/d1/projects", interval: metadataWarmInterval, states: []metadataScanState{{driveID: "d1", ucloudPath: "/d1/projects"}}},
		{driveID: "d1", ucloudPath: "/d1/project-x", interval: metadataHotInterval},
		{driveID: "d2", ucloudPath: "/d2/projects", interval: metadataWarmInterval},
	}

	result := metadataCollapseScanCandidates(candidates)
	if len(result) != 3 {
		t.Fatalf("expected 3 scan roots, got %#v", result)
	}
	var projects metadataScanCandidate
	for _, candidate := range result {
		if candidate.ucloudPath == "/d1/projects" {
			projects = candidate
		}
	}
	if projects.ucloudPath == "" || projects.interval != metadataActiveInterval || len(projects.states) != 2 {
		t.Fatalf("unexpected collapsed candidate: %#v", projects)
	}
	if metadataUCloudPathContains("/d1/projects", "/d1/project-x") {
		t.Fatal("similarly named sibling was treated as a descendant")
	}
}

func TestMetadataCoalescePendingRequests(t *testing.T) {
	firstCompletion := func(error) {}
	secondCompletion := func(error) {}
	queue := []metadataScanRequest{{internalPath: "/mnt/drive/projects/alpha", completions: []func(error){firstCompletion}}}
	queue = metadataCoalesceScanRequest(queue, metadataScanRequest{
		internalPath: "/mnt/drive/projects",
		completions:  []func(error){secondCompletion},
	})
	if len(queue) != 1 || queue[0].internalPath != "/mnt/drive/projects" || len(queue[0].completions) != 2 {
		t.Fatalf("unexpected coalesced queue: %#v", queue)
	}
}
