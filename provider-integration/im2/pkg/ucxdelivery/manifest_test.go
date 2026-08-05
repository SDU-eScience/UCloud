package ucxdelivery

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
)

func TestVerifyManifest(t *testing.T) {
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("generate key: %s", err)
	}

	manifestBytes := testManifestBytes(t, "hello")
	signature := ed25519.Sign(privateKey, manifestBytes)

	manifest, err := VerifyManifest(manifestBytes, signature, PublicKeyPrefix+base64.StdEncoding.EncodeToString(publicKey))
	if err != nil {
		t.Fatalf("verify manifest: %s", err)
	}
	if manifest.BinaryUrl != "https://provider.example.org/ucloud/ucx/my-app/my-ucx-app" {
		t.Fatalf("unexpected binary URL: %s", manifest.BinaryUrl)
	}
}

func TestVerifyManifestRejectsChangedManifest(t *testing.T) {
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("generate key: %s", err)
	}

	manifestBytes := testManifestBytes(t, "hello")
	signature := ed25519.Sign(privateKey, manifestBytes)
	changedManifestBytes := testManifestBytes(t, "changed")

	_, err = VerifyManifest(changedManifestBytes, signature, PublicKeyPrefix+base64.StdEncoding.EncodeToString(publicKey))
	if err == nil {
		t.Fatalf("expected changed manifest to fail verification")
	}
}

func TestVerifyBinaryRejectsMismatchedSha256(t *testing.T) {
	expected := sha256.Sum256([]byte("hello"))
	if err := VerifyBinary([]byte("changed"), hex.EncodeToString(expected[:])); err == nil {
		t.Fatalf("expected binary SHA-256 mismatch")
	}
}

func TestSignatureUrl(t *testing.T) {
	got := SignatureUrl("https://provider.example.org/ucloud/ucx/my-app/manifest.json")
	want := "https://provider.example.org/ucloud/ucx/my-app/manifest.json.sig"
	if got != want {
		t.Fatalf("SignatureUrl() = %q, want %q", got, want)
	}
}

func TestFetchAndVerifyManifest(t *testing.T) {
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("generate key: %s", err)
	}

	manifestBytes := testManifestBytes(t, "hello")
	signature := ed25519.Sign(privateKey, manifestBytes)

	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/manifest.json":
			_, _ = w.Write(manifestBytes)
		case "/manifest.json.sig":
			_, _ = w.Write(signature)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	manifest, fetchedBytes, err := FetchAndVerifyManifest(
		context.Background(),
		server.Client(),
		server.URL+"/manifest.json",
		PublicKeyPrefix+base64.StdEncoding.EncodeToString(publicKey),
	)
	if err != nil {
		t.Fatalf("fetch and verify manifest: %s", err)
	}
	if string(fetchedBytes) != string(manifestBytes) {
		t.Fatalf("unexpected fetched manifest bytes")
	}
	if manifest.Sha256 == "" {
		t.Fatalf("expected manifest SHA-256")
	}
}

func TestFetchAndVerifyManifestRejectsHttpUrl(t *testing.T) {
	_, _, err := FetchAndVerifyManifest(context.Background(), nil, "http://provider.example.org/manifest.json", "ed25519:key")
	if err == nil {
		t.Fatalf("expected HTTP manifest URL to be rejected")
	}
}

func TestFetchAndVerifyBinary(t *testing.T) {
	binary := []byte("hello")
	sum := sha256.Sum256(binary)

	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(binary)
	}))
	defer server.Close()

	fetched, err := FetchAndVerifyBinary(context.Background(), server.Client(), Manifest{
		BinaryUrl: server.URL + "/my-ucx-app",
		Sha256:    hex.EncodeToString(sum[:]),
		UpdatedAt: fndapi.Timestamp(time.Date(2026, 7, 7, 12, 0, 0, 0, time.UTC)),
	})
	if err != nil {
		t.Fatalf("fetch and verify binary: %s", err)
	}
	if string(fetched) != string(binary) {
		t.Fatalf("unexpected binary bytes")
	}
}

func testManifestBytes(t *testing.T, binary string) []byte {
	t.Helper()

	sum := sha256.Sum256([]byte(binary))
	manifest := Manifest{
		BinaryUrl: "https://provider.example.org/ucloud/ucx/my-app/my-ucx-app",
		Sha256:    hex.EncodeToString(sum[:]),
		UpdatedAt: fndapi.Timestamp(time.Date(2026, 7, 7, 12, 0, 0, 0, time.UTC)),
	}
	data, err := json.Marshal(manifest)
	if err != nil {
		t.Fatalf("marshal manifest: %s", err)
	}
	return data
}
