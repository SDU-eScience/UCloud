package ucxdelivery

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestBuildDeliveryUrls(t *testing.T) {
	urls, err := BuildDeliveryUrls("provider.example.org", "my-app", "1.0.0", "my-ucx-app")
	if err != nil {
		t.Fatalf("build URLs: %s", err)
	}

	if urls.BinaryUrl != "https://provider.example.org/ucx/my-app/1.0.0/my-ucx-app" {
		t.Fatalf("unexpected binary URL: %s", urls.BinaryUrl)
	}
	if urls.ManifestUrl != "https://provider.example.org/ucx/my-app/1.0.0/manifest.json" {
		t.Fatalf("unexpected manifest URL: %s", urls.ManifestUrl)
	}
	if urls.SignatureUrl != "https://provider.example.org/ucx/my-app/1.0.0/manifest.json.sig" {
		t.Fatalf("unexpected signature URL: %s", urls.SignatureUrl)
	}
}

func TestSignBinary(t *testing.T) {
	dir := t.TempDir()
	binaryPath := filepath.Join(dir, "my-ucx-app")
	privatePath := filepath.Join(dir, "keys", "ucx-signing.key")
	publicPath := filepath.Join(dir, "keys", "ucx-signing.pub")
	manifestPath := filepath.Join(dir, "manifest.json")
	signaturePath := filepath.Join(dir, "manifest.json.sig")

	if err := os.WriteFile(binaryPath, []byte("hello"), 0755); err != nil {
		t.Fatalf("write binary: %s", err)
	}
	keys, err := WriteKeyPair(privatePath, publicPath)
	if err != nil {
		t.Fatalf("write key pair: %s", err)
	}

	result, err := SignBinary(SignOptions{
		BinaryPath:     binaryPath,
		ProviderDomain: "provider.example.org",
		AppName:        "my-app",
		AppVersion:     "1.0.0",
		PrivateKeyPath: privatePath,
		UpdatedAt:      time.Date(2026, 7, 7, 12, 0, 0, 0, time.UTC),
	})
	if err != nil {
		t.Fatalf("sign binary: %s", err)
	}

	if result.Manifest.BinaryUrl != "https://provider.example.org/ucx/my-app/1.0.0/my-ucx-app" {
		t.Fatalf("unexpected binary URL: %s", result.Manifest.BinaryUrl)
	}
	if result.ManifestUrl != "https://provider.example.org/ucx/my-app/1.0.0/manifest.json" {
		t.Fatalf("unexpected manifest URL: %s", result.ManifestUrl)
	}
	if result.PublicKey != keys.PublicKey {
		t.Fatalf("unexpected public key")
	}

	manifestBytes, err := os.ReadFile(manifestPath)
	if err != nil {
		t.Fatalf("read manifest: %s", err)
	}
	signature, err := os.ReadFile(signaturePath)
	if err != nil {
		t.Fatalf("read signature: %s", err)
	}
	if _, err := VerifyManifest(manifestBytes, signature, keys.PublicKey); err != nil {
		t.Fatalf("verify manifest: %s", err)
	}
}

func TestSignBinaryChangesAfterBinaryChanges(t *testing.T) {
	dir := t.TempDir()
	binaryPath := filepath.Join(dir, "my-ucx-app")
	privatePath := filepath.Join(dir, "ucx-signing.key")
	publicPath := filepath.Join(dir, "ucx-signing.pub")
	signaturePath := filepath.Join(dir, "manifest.json.sig")

	if _, err := WriteKeyPair(privatePath, publicPath); err != nil {
		t.Fatalf("write key pair: %s", err)
	}
	if err := os.WriteFile(binaryPath, []byte("hello"), 0755); err != nil {
		t.Fatalf("write binary: %s", err)
	}

	options := SignOptions{
		BinaryPath:     binaryPath,
		ProviderDomain: "provider.example.org",
		AppName:        "my-app",
		AppVersion:     "1.0.0",
		PrivateKeyPath: privatePath,
		UpdatedAt:      time.Date(2026, 7, 7, 12, 0, 0, 0, time.UTC),
	}
	first, err := SignBinary(options)
	if err != nil {
		t.Fatalf("first sign: %s", err)
	}
	firstSig, err := os.ReadFile(signaturePath)
	if err != nil {
		t.Fatalf("read first signature: %s", err)
	}

	if err := os.WriteFile(binaryPath, []byte("changed"), 0755); err != nil {
		t.Fatalf("change binary: %s", err)
	}
	second, err := SignBinary(options)
	if err != nil {
		t.Fatalf("second sign: %s", err)
	}
	secondSig, err := os.ReadFile(signaturePath)
	if err != nil {
		t.Fatalf("read second signature: %s", err)
	}

	if first.Manifest.Sha256 == second.Manifest.Sha256 {
		t.Fatalf("expected SHA-256 to change after binary changes")
	}
	if bytes.Equal(firstSig, secondSig) {
		t.Fatalf("expected signature to change after binary changes")
	}
}

func TestSignCliPrintsCatalogMetadata(t *testing.T) {
	dir := t.TempDir()
	binaryPath := filepath.Join(dir, "my-ucx-app")
	privatePath := filepath.Join(dir, "ucx-signing.key")
	publicPath := filepath.Join(dir, "ucx-signing.pub")

	if err := os.WriteFile(binaryPath, []byte("hello"), 0755); err != nil {
		t.Fatalf("write binary: %s", err)
	}
	if _, err := WriteKeyPair(privatePath, publicPath); err != nil {
		t.Fatalf("write key pair: %s", err)
	}

	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}
	code := SignCli([]string{
		"--binary", binaryPath,
		"--provider-domain", "provider.example.org",
		"--app-name", "my-app",
		"--app-version", "1.0.0",
		"--private-key", privatePath,
	}, stdout, stderr)
	if code != 0 {
		t.Fatalf("SignCli exit %d: %s", code, stderr.String())
	}

	output := stdout.String()
	for _, expected := range []string{
		"manifestUrl: https://provider.example.org/ucx/my-app/1.0.0/manifest.json",
		"publicKey: ed25519:",
		"binaryName: my-ucx-app",
	} {
		if !strings.Contains(output, expected) {
			t.Fatalf("missing %q in output:\n%s", expected, output)
		}
	}
}
