package tools

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const webFetchCacheLifetime = 10 * time.Minute

type webFetchDocument struct {
	ID                string    `json:"id"`
	RequestURL        string    `json:"request_url"`
	URL               string    `json:"url"`
	Status            int       `json:"status"`
	ContentType       string    `json:"content_type"`
	Format            string    `json:"format"`
	Content           string    `json:"content"`
	DownloadTruncated bool      `json:"download_truncated"`
	CreatedAt         time.Time `json:"created_at"`
}

func webFetchCacheRead(path string) (webFetchDocument, error) {
	var document webFetchDocument
	data, err := os.ReadFile(path)
	if err != nil {
		return document, err
	}
	err = json.Unmarshal(data, &document)
	if err != nil {
		return document, err
	}
	if time.Since(document.CreatedAt) >= webFetchCacheLifetime {
		return document, fmt.Errorf("cached document expired; fetch the URL again")
	}
	return document, nil
}

func webFetchCached(args webFetchPayload) error {
	args.URL = strings.TrimSpace(args.URL)
	args.Query = strings.TrimSpace(args.Query)
	args.Format = strings.ToLower(strings.TrimSpace(args.Format))
	if args.Format == "" {
		args.Format = "markdown"
	}
	if args.Format != "markdown" && args.Format != "html" {
		return fmt.Errorf("format must be markdown or html")
	}
	if args.MaxTokens < 0 {
		return fmt.Errorf("max_tokens must not be negative")
	}
	if args.Cursor != "" && args.DocumentID == "" {
		return fmt.Errorf("cursor requires document_id")
	}
	directory := filepath.Join(os.TempDir(), "ucloud-web-fetch")
	if err := os.MkdirAll(directory, 0700); err != nil {
		return err
	}
	if err := webFetchCacheCleanup(directory); err != nil {
		return err
	}
	var document webFetchDocument
	var err error
	cached := false
	if args.DocumentID != "" {
		decoded, decodeErr := hex.DecodeString(args.DocumentID)
		if decodeErr != nil || len(decoded) != 32 {
			return fmt.Errorf("invalid document_id")
		}
		document, err = webFetchCacheRead(filepath.Join(directory, args.DocumentID+".json"))
		if err != nil {
			return fmt.Errorf("cached document is unavailable; fetch the URL again: %w", err)
		}
		if args.URL != "" && args.URL != document.RequestURL && args.URL != document.URL {
			return fmt.Errorf("document_id does not match URL")
		}
		cached = true
	} else {
		if args.URL == "" {
			return fmt.Errorf("url or document_id is required")
		}
		key := webFetchDigest(args.URL + "\n" + args.Format)
		if reference, readErr := os.ReadFile(filepath.Join(directory, key+".ref")); readErr == nil {
			id := string(reference)
			decoded, decodeErr := hex.DecodeString(id)
			if decodeErr == nil && len(decoded) == 32 {
				candidate, candidateErr := webFetchCacheRead(filepath.Join(directory, id+".json"))
				if candidateErr == nil && candidate.RequestURL == args.URL && candidate.Format == args.Format {
					document, cached = candidate, true
				}
			}
		}
		if !cached {
			document, err = webFetchDownload(args)
			if err != nil {
				return err
			}
			document.ID = webFetchDigest(args.URL + "\n" + args.Format + "\n" + document.CreatedAt.Format(time.RFC3339Nano) + "\n" + document.Content)
			if err = webFetchCacheStore(directory, document); err != nil {
				return err
			}
		}
	}
	result, err := webFetchPage(document, args)
	if err != nil {
		return err
	}
	result["cached"] = cached
	encoded, err := json.Marshal(result)
	if err != nil {
		return err
	}
	fmt.Println(string(encoded))
	return nil
}

func webFetchCacheStore(directory string, document webFetchDocument) error {
	data, err := json.Marshal(document)
	if err != nil {
		return err
	}
	if err = webFetchCacheWrite(directory, document.ID+".json", data); err != nil {
		return err
	}
	key := webFetchDigest(document.RequestURL + "\n" + document.Format)
	return webFetchCacheWrite(directory, key+".ref", []byte(document.ID))
}

func webFetchCacheCleanup(directory string) error {
	entries, err := os.ReadDir(directory)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if !strings.HasSuffix(entry.Name(), ".json") && !strings.HasSuffix(entry.Name(), ".ref") && !strings.HasPrefix(entry.Name(), "document-") {
			continue
		}
		info, infoErr := entry.Info()
		if infoErr != nil {
			if os.IsNotExist(infoErr) {
				continue
			}
			return infoErr
		}
		if info.IsDir() || time.Since(info.ModTime()) < webFetchCacheLifetime {
			continue
		}
		if err = os.Remove(filepath.Join(directory, entry.Name())); err != nil && !os.IsNotExist(err) {
			return err
		}
	}
	return nil
}

func webFetchCacheWrite(directory, name string, data []byte) error {
	file, err := os.CreateTemp(directory, "document-")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	if _, err = file.Write(data); err != nil {
		file.Close()
		return err
	}
	if err = file.Close(); err != nil {
		return err
	}
	return os.Rename(file.Name(), filepath.Join(directory, name))
}
