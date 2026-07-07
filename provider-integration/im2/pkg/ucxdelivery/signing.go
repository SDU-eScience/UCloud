package ucxdelivery

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
)

const PrivateKeyPrefix = "ed25519-private:"

type KeyPair struct {
	PrivateKey string
	PublicKey  string
}

type DeliveryUrls struct {
	BinaryUrl    string
	ManifestUrl  string
	SignatureUrl string
}

type SignOptions struct {
	BinaryPath     string
	ProviderDomain string
	AppName        string
	AppVersion     string
	ManifestPath   string
	SignaturePath  string
	PrivateKeyPath string
	UpdatedAt      time.Time
}

type SignResult struct {
	Manifest     Manifest
	ManifestUrl  string
	SignatureUrl string
	PublicKey    string
	BinaryName   string
}

func GenerateKeyPair() (KeyPair, error) {
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return KeyPair{}, err
	}

	return KeyPair{
		PrivateKey: PrivateKeyPrefix + base64.StdEncoding.EncodeToString(privateKey),
		PublicKey:  PublicKeyPrefix + base64.StdEncoding.EncodeToString(publicKey),
	}, nil
}

func BuildDeliveryUrls(providerDomain string, appName string, appVersion string, binaryName string) (DeliveryUrls, error) {
	domain := strings.TrimSpace(providerDomain)
	domain = strings.TrimPrefix(domain, "https://")
	domain = strings.TrimSuffix(domain, "/")
	if domain == "" || strings.Contains(domain, "/") || strings.Contains(domain, "://") {
		return DeliveryUrls{}, fmt.Errorf("provider domain must be a domain name without scheme or path")
	}
	if strings.TrimSpace(appName) == "" {
		return DeliveryUrls{}, fmt.Errorf("app name is required")
	}
	if strings.TrimSpace(appVersion) == "" {
		return DeliveryUrls{}, fmt.Errorf("app version is required")
	}
	if strings.TrimSpace(binaryName) == "" || binaryName != path.Base(binaryName) {
		return DeliveryUrls{}, fmt.Errorf("binary name must be a file name")
	}

	basePath := path.Join(
		"/ucx",
		url.PathEscape(appName),
		url.PathEscape(appVersion),
	)
	binaryUrl := "https://" + domain + basePath + "/" + url.PathEscape(binaryName)
	manifestUrl := "https://" + domain + basePath + "/manifest.json"

	return DeliveryUrls{
		BinaryUrl:    binaryUrl,
		ManifestUrl:  manifestUrl,
		SignatureUrl: SignatureUrl(manifestUrl),
	}, nil
}

func ManifestBytes(manifest Manifest) ([]byte, error) {
	if err := ValidateManifest(manifest); err != nil {
		return nil, err
	}
	return json.Marshal(manifest)
}

func SignManifest(manifestBytes []byte, privateKey string) ([]byte, string, error) {
	private, public, err := parseEd25519PrivateKey(privateKey)
	if err != nil {
		return nil, "", err
	}

	return ed25519.Sign(private, manifestBytes), PublicKeyPrefix + base64.StdEncoding.EncodeToString(public), nil
}

func SignBinary(options SignOptions) (SignResult, error) {
	if strings.TrimSpace(options.BinaryPath) == "" {
		return SignResult{}, fmt.Errorf("binary path is required")
	}
	if strings.TrimSpace(options.ManifestPath) == "" {
		return SignResult{}, fmt.Errorf("manifest path is required")
	}
	if strings.TrimSpace(options.SignaturePath) == "" {
		return SignResult{}, fmt.Errorf("signature path is required")
	}
	if strings.TrimSpace(options.PrivateKeyPath) == "" {
		return SignResult{}, fmt.Errorf("private key path is required")
	}

	binary, err := os.ReadFile(options.BinaryPath)
	if err != nil {
		return SignResult{}, err
	}
	binaryName := filepath.Base(options.BinaryPath)
	urls, err := BuildDeliveryUrls(options.ProviderDomain, options.AppName, options.AppVersion, binaryName)
	if err != nil {
		return SignResult{}, err
	}

	updatedAt := options.UpdatedAt
	if updatedAt.IsZero() {
		info, err := os.Stat(options.BinaryPath)
		if err != nil {
			return SignResult{}, err
		}
		updatedAt = info.ModTime().UTC().Truncate(time.Second)
	} else {
		updatedAt = updatedAt.UTC().Truncate(time.Second)
	}

	sum := sha256.Sum256(binary)
	manifest := Manifest{
		BinaryUrl: urls.BinaryUrl,
		Sha256:    hex.EncodeToString(sum[:]),
		UpdatedAt: fndapi.Timestamp(updatedAt),
	}
	manifestBytes, err := ManifestBytes(manifest)
	if err != nil {
		return SignResult{}, err
	}

	privateKeyBytes, err := os.ReadFile(options.PrivateKeyPath)
	if err != nil {
		return SignResult{}, err
	}
	signature, publicKey, err := SignManifest(manifestBytes, strings.TrimSpace(string(privateKeyBytes)))
	if err != nil {
		return SignResult{}, err
	}

	if err := ensureParentDirectory(options.ManifestPath); err != nil {
		return SignResult{}, err
	}
	if err := os.WriteFile(options.ManifestPath, manifestBytes, 0644); err != nil {
		return SignResult{}, err
	}
	if err := ensureParentDirectory(options.SignaturePath); err != nil {
		return SignResult{}, err
	}
	if err := os.WriteFile(options.SignaturePath, signature, 0644); err != nil {
		return SignResult{}, err
	}

	return SignResult{
		Manifest:     manifest,
		ManifestUrl:  urls.ManifestUrl,
		SignatureUrl: urls.SignatureUrl,
		PublicKey:    publicKey,
		BinaryName:   binaryName,
	}, nil
}

func WriteKeyPair(privatePath string, publicPath string) (KeyPair, error) {
	if strings.TrimSpace(privatePath) == "" {
		return KeyPair{}, fmt.Errorf("private key path is required")
	}
	if strings.TrimSpace(publicPath) == "" {
		return KeyPair{}, fmt.Errorf("public key path is required")
	}

	keys, err := GenerateKeyPair()
	if err != nil {
		return KeyPair{}, err
	}
	if err := ensureParentDirectory(privatePath); err != nil {
		return KeyPair{}, err
	}
	if err := os.WriteFile(privatePath, []byte(keys.PrivateKey+"\n"), 0600); err != nil {
		return KeyPair{}, err
	}
	if err := ensureParentDirectory(publicPath); err != nil {
		return KeyPair{}, err
	}
	if err := os.WriteFile(publicPath, []byte(keys.PublicKey+"\n"), 0644); err != nil {
		return KeyPair{}, err
	}

	return keys, nil
}

func parseEd25519PrivateKey(privateKey string) (ed25519.PrivateKey, ed25519.PublicKey, error) {
	if !strings.HasPrefix(privateKey, PrivateKeyPrefix) {
		return nil, nil, fmt.Errorf("private key must use the ed25519-private: prefix")
	}

	decoded, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(privateKey, PrivateKeyPrefix))
	if err != nil {
		return nil, nil, fmt.Errorf("invalid ed25519 private key encoding: %w", err)
	}
	if len(decoded) != ed25519.PrivateKeySize {
		return nil, nil, fmt.Errorf("ed25519 private key must be %d bytes", ed25519.PrivateKeySize)
	}

	private := ed25519.PrivateKey(decoded)
	public, ok := private.Public().(ed25519.PublicKey)
	if !ok {
		return nil, nil, fmt.Errorf("failed to derive ed25519 public key")
	}

	return private, public, nil
}

func ensureParentDirectory(filePath string) error {
	parent := filepath.Dir(filePath)
	if parent == "." || parent == "" {
		return nil
	}
	return os.MkdirAll(parent, 0755)
}
