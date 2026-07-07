package ucxdelivery

import (
	"flag"
	"fmt"
	"io"
)

func KeygenCli(args []string, stdout io.Writer, stderr io.Writer) int {
	flags := flag.NewFlagSet("ucx-keygen", flag.ContinueOnError)
	flags.SetOutput(stderr)
	privatePath := flags.String("private", "", "Path to write the Ed25519 private key")
	publicPath := flags.String("public", "", "Path to write the Ed25519 public key")
	if err := flags.Parse(args); err != nil {
		return 2
	}

	keys, err := WriteKeyPair(*privatePath, *publicPath)
	if err != nil {
		_, _ = fmt.Fprintf(stderr, "ucx-keygen: %s\n", err)
		return 1
	}

	_, _ = fmt.Fprintf(stdout, "publicKey: %s\n", keys.PublicKey)
	return 0
}

func SignCli(args []string, stdout io.Writer, stderr io.Writer) int {
	flags := flag.NewFlagSet("ucx-sign", flag.ContinueOnError)
	flags.SetOutput(stderr)
	binaryPath := flags.String("binary", "", "Path to the UCX executable")
	providerDomain := flags.String("provider-domain", "", "Provider domain, without scheme or path")
	appName := flags.String("app-name", "", "Catalog app name")
	appVersion := flags.String("app-version", "", "Catalog app version")
	manifestPath := flags.String("manifest", "", "Path to write manifest.json")
	signaturePath := flags.String("signature", "", "Path to write manifest.json.sig")
	privateKeyPath := flags.String("private-key", "", "Path to the Ed25519 private key")
	if err := flags.Parse(args); err != nil {
		return 2
	}

	result, err := SignBinary(SignOptions{
		BinaryPath:     *binaryPath,
		ProviderDomain: *providerDomain,
		AppName:        *appName,
		AppVersion:     *appVersion,
		ManifestPath:   *manifestPath,
		SignaturePath:  *signaturePath,
		PrivateKeyPath: *privateKeyPath,
	})
	if err != nil {
		_, _ = fmt.Fprintf(stderr, "ucx-sign: %s\n", err)
		return 1
	}

	_, _ = fmt.Fprintf(stdout, "manifestUrl: %s\n", result.ManifestUrl)
	_, _ = fmt.Fprintf(stdout, "publicKey: %s\n", result.PublicKey)
	_, _ = fmt.Fprintf(stdout, "binaryName: %s\n", result.BinaryName)
	return 0
}
