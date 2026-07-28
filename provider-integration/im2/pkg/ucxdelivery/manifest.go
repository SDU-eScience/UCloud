package ucxdelivery

import (
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"

	cfg "ucloud.dk/pkg/config"
	fndapi "ucloud.dk/shared/pkg/foundation"
)

const (
	PublicKeyPrefix = "ed25519:"
	SignatureSuffix = ".sig"
)

type Manifest struct {
	BinaryUrl string           `json:"binaryUrl"`
	Sha256    string           `json:"sha256"`
	UpdatedAt fndapi.Timestamp `json:"updatedAt"`
}

func SignatureUrl(manifestUrl string) string {
	return manifestUrl + SignatureSuffix
}

func VerifyManifest(manifestBytes []byte, signature []byte, publicKey string) (Manifest, error) {
	key, err := parseEd25519PublicKey(publicKey)
	if err != nil {
		return Manifest{}, err
	}

	if len(signature) != ed25519.SignatureSize {
		return Manifest{}, fmt.Errorf("manifest signature must be %d bytes", ed25519.SignatureSize)
	}

	if !ed25519.Verify(key, manifestBytes, signature) {
		return Manifest{}, fmt.Errorf("manifest signature verification failed")
	}

	var manifest Manifest
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return Manifest{}, fmt.Errorf("invalid manifest JSON: %w", err)
	}
	if err := ValidateManifest(manifest); err != nil {
		return Manifest{}, err
	}

	return manifest, nil
}

func ValidateManifest(manifest Manifest) error {
	if err := requireHttpsUrl(manifest.BinaryUrl, "binaryUrl"); err != nil {
		return err
	}
	if strings.TrimSpace(manifest.Sha256) == "" {
		return fmt.Errorf("sha256 is required")
	}
	decoded, err := hex.DecodeString(manifest.Sha256)
	if err != nil || len(decoded) != sha256.Size {
		return fmt.Errorf("sha256 must be a hex-encoded SHA-256 digest")
	}
	if manifest.UpdatedAt.Time().IsZero() {
		return fmt.Errorf("updatedAt is required")
	}

	return nil
}

func VerifyBinary(binary []byte, expectedSha256 string) error {
	decoded, err := hex.DecodeString(expectedSha256)
	if err != nil || len(decoded) != sha256.Size {
		return fmt.Errorf("sha256 must be a hex-encoded SHA-256 digest")
	}

	sum := sha256.Sum256(binary)
	if subtle.ConstantTimeCompare(sum[:], decoded) != 1 {
		return fmt.Errorf("binary SHA-256 mismatch")
	}

	return nil
}

func FetchAndVerifyManifest(ctx context.Context, client *http.Client, manifestUrl string, publicKey string) (Manifest, []byte, error) {
	if err := requireHttpsUrl(manifestUrl, "manifestUrl"); err != nil {
		return Manifest{}, nil, err
	}

	manifestBytes, err := fetchBytes(ctx, client, manifestUrl)
	if err != nil {
		return Manifest{}, nil, err
	}
	// The .sig file contains the raw Ed25519 signature over the exact manifest bytes.
	signature, err := fetchBytes(ctx, client, SignatureUrl(manifestUrl))
	if err != nil {
		return Manifest{}, nil, err
	}

	manifest, err := VerifyManifest(manifestBytes, signature, publicKey)
	if err != nil {
		return Manifest{}, nil, err
	}

	return manifest, manifestBytes, nil
}

func FetchAndVerifyBinary(ctx context.Context, client *http.Client, manifest Manifest) ([]byte, error) {
	if err := ValidateManifest(manifest); err != nil {
		return nil, err
	}

	binary, err := fetchBytes(ctx, client, manifest.BinaryUrl)
	if err != nil {
		return nil, err
	}
	if err := VerifyBinary(binary, manifest.Sha256); err != nil {
		return nil, err
	}

	return binary, nil
}

func parseEd25519PublicKey(publicKey string) (ed25519.PublicKey, error) {
	if !strings.HasPrefix(publicKey, PublicKeyPrefix) {
		return nil, fmt.Errorf("public key must use the ed25519: prefix")
	}

	decoded, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(publicKey, PublicKeyPrefix))
	if err != nil {
		return nil, fmt.Errorf("invalid ed25519 public key encoding: %w", err)
	}
	if len(decoded) != ed25519.PublicKeySize {
		return nil, fmt.Errorf("ed25519 public key must be %d bytes", ed25519.PublicKeySize)
	}

	return ed25519.PublicKey(decoded), nil
}

func requireHttpsUrl(rawUrl string, field string) error {
	parsed, err := url.Parse(rawUrl)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" {
		return fmt.Errorf("%s must be an HTTPS URL", field)
	}
	return nil
}

func fetchBytes(ctx context.Context, client *http.Client, url string) ([]byte, error) {
	if cfg.Provider != nil && strings.HasPrefix(url, cfg.Provider.Hosts.SelfPublic.ToURL()) {
		withoutPrefix, _ := strings.CutPrefix(url, cfg.Provider.Hosts.SelfPublic.ToURL())
		url = cfg.Provider.Hosts.Self.ToURL() + withoutPrefix
	}
	if client == nil {
		client = http.DefaultClient
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("GET %s failed with status %d", url, resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	return body, nil
}
