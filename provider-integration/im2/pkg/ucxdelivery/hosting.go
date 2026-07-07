package ucxdelivery

import (
	"fmt"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const PublishedDirectory = "ucloud-ucx-published"

func RegisterStaticHandler(mux *http.ServeMux, hostingRoot string) error {
	if mux == nil {
		return fmt.Errorf("HTTP mux is required")
	}
	handler, err := StaticHandler(hostingRoot)
	if err != nil {
		return err
	}
	mux.Handle("/ucx/", handler)
	return nil
}

func StaticHandler(hostingRoot string) (http.Handler, error) {
	if strings.TrimSpace(hostingRoot) == "" {
		return nil, fmt.Errorf("hosting root is required")
	}
	root, err := filepath.Abs(hostingRoot)
	if err != nil {
		return nil, err
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}

		filePath, err := publishedFilePath(root, r.URL.Path)
		if err != nil {
			http.NotFound(w, r)
			return
		}

		if contentType := contentTypeForPublishedFile(filePath); contentType != "" {
			w.Header().Set("Content-Type", contentType)
		}
		http.ServeFile(w, r, filePath)
	}), nil
}

func publishedFilePath(root string, requestPath string) (string, error) {
	parts := strings.Split(strings.TrimPrefix(requestPath, "/"), "/")
	if len(parts) != 4 || parts[0] != "ucx" {
		return "", fmt.Errorf("invalid UCX path")
	}
	for _, part := range parts[1:] {
		if part == "" || part == "." || part == ".." || strings.Contains(part, "\\") {
			return "", fmt.Errorf("invalid path component")
		}
	}

	candidate := filepath.Join(root, PublishedDirectory, "apps", parts[1], parts[2], "current", parts[3])
	resolved, err := filepath.EvalSymlinks(candidate)
	if err != nil {
		return "", err
	}
	if !pathIsInside(root, resolved) {
		return "", fmt.Errorf("path escapes hosting root")
	}
	info, err := os.Stat(resolved)
	if err != nil {
		return "", err
	}
	if info.IsDir() {
		return "", fmt.Errorf("directories are not served")
	}

	return resolved, nil
}

func pathIsInside(root string, path string) bool {
	rel, err := filepath.Rel(root, path)
	if err != nil {
		return false
	}
	return rel == "." || (!strings.HasPrefix(rel, ".."+string(filepath.Separator)) && rel != "..")
}

func contentTypeForPublishedFile(path string) string {
	switch filepath.Base(path) {
	case "manifest.json":
		return "application/json"
	case "manifest.json.sig":
		return "application/octet-stream"
	default:
		return mime.TypeByExtension(filepath.Ext(path))
	}
}
