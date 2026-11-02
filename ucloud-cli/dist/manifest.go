package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

type platform struct {
	OS           string `json:"os"`
	Architecture string `json:"architecture"`
	File         string `json:"file"`
	SHA256       string `json:"sha256"`
}

type manifest struct {
	Version   string     `json:"version"`
	Platforms []platform `json:"platforms"`
}

func main() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "Usage: manifest VERSION DIRECTORY OUTPUT")
		os.Exit(2)
	}

	version := os.Args[1]
	directory := os.Args[2]
	output := os.Args[3]
	result := manifest{Version: version}

	for _, osName := range []string{"linux", "darwin"} {
		for _, architecture := range []string{"amd64", "arm64"} {
			filename := fmt.Sprintf("ucloud-%s-%s-%s.tar.gz", version, osName, architecture)
			data, err := os.ReadFile(filepath.Join(directory, filename))
			if err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}

			digest := sha256.Sum256(data)
			result.Platforms = append(result.Platforms, platform{
				OS:           osName,
				Architecture: architecture,
				File:         filename,
				SHA256:       hex.EncodeToString(digest[:]),
			})
		}
	}

	data, err := json.MarshalIndent(result, "", "  ")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	if err := os.WriteFile(output, append(data, '\n'), 0644); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
