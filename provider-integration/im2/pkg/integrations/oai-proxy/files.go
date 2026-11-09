package oai_proxy

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"ucloud.dk/shared/pkg/log"

	"golang.org/x/sync/singleflight"
)

type oaiProxyFileReplacement struct {
	DownloadURL string
	DataURL     string
	Base64      string
	Large       bool
	Image       bool
}

type oaiProxySpoolEntry struct {
	Path        string
	Name        string
	ContentType string
	ExpiresAt   time.Time
}

type oaiProxyCachedFile struct {
	Replacement oaiProxyFileReplacement
	ExpiresAt   time.Time
	Size        int64
}

type oaiProxyFileStore struct {
	rootPath      string
	spoolRoot     string
	spoolDir      string
	publicURL     *url.URL
	inlineLimit   int64
	maxFileBytes  int64
	cacheMaxBytes int64
	ttl           time.Duration

	mu           sync.Mutex
	entries      map[string]oaiProxySpoolEntry
	cache        map[string]oaiProxyCachedFile
	cacheBytes   int64
	resolveGroup singleflight.Group
}

func oaiProxyFileStoreNew(cfg *oaiProxyConfig) (*oaiProxyFileStore, error) {
	if err := os.MkdirAll(cfg.spoolRoot, 0700); err != nil {
		return nil, err
	}

	store := &oaiProxyFileStore{
		rootPath:      cfg.fileRoot,
		spoolRoot:     cfg.spoolRoot,
		publicURL:     cfg.publicURL,
		inlineLimit:   cfg.inlineLimit,
		maxFileBytes:  cfg.maxFileBytes,
		cacheMaxBytes: cfg.cacheMaxBytes,
		ttl:           cfg.fileTTL,
		entries:       map[string]oaiProxySpoolEntry{},
		cache:         map[string]oaiProxyCachedFile{},
	}

	store.cleanupStale()

	spoolDir, err := os.MkdirTemp(cfg.spoolRoot, "proxy-")
	if err != nil {
		return nil, err
	}
	store.spoolDir = spoolDir
	return store, nil
}

func (s *oaiProxyFileStore) Close() {
	s.mu.Lock()
	s.entries = map[string]oaiProxySpoolEntry{}
	s.cache = map[string]oaiProxyCachedFile{}
	s.cacheBytes = 0
	s.mu.Unlock()

	if s.spoolDir != "" {
		_ = os.RemoveAll(s.spoolDir)
	}
}

func (s *oaiProxyFileStore) RunJanitor(ctx context.Context) {
	interval := s.ttl / 2
	if interval > time.Minute {
		interval = time.Minute
	}
	if interval < time.Second {
		interval = time.Second
	}

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			s.cleanupExpired()
		}
	}
}

func (s *oaiProxyFileStore) cleanupStale() {
	dirs, err := os.ReadDir(s.spoolRoot)
	if err != nil {
		return
	}

	now := time.Now()
	for _, dir := range dirs {
		if !dir.IsDir() {
			continue
		}

		dirPath := filepath.Join(s.spoolRoot, dir.Name())
		files, err := os.ReadDir(dirPath)
		if err != nil {
			continue
		}

		for _, file := range files {
			if file.IsDir() {
				continue
			}

			info, err := file.Info()
			if err != nil {
				continue
			}
			if now.Sub(info.ModTime()) > s.ttl {
				_ = os.Remove(filepath.Join(dirPath, file.Name()))
			}
		}
	}
}

func (s *oaiProxyFileStore) cleanupExpired() {
	now := time.Now()

	s.mu.Lock()
	var expired []string
	for id, entry := range s.entries {
		if !now.Before(entry.ExpiresAt) {
			expired = append(expired, entry.Path)
			delete(s.entries, id)
		}
	}
	for raw, cached := range s.cache {
		if !now.Before(cached.ExpiresAt) {
			delete(s.cache, raw)
			s.cacheBytes -= cached.Size
		}
	}
	s.mu.Unlock()

	for _, path := range expired {
		_ = os.Remove(path)
	}
}

func (s *oaiProxyFileStore) ResolveFileURL(raw string) (oaiProxyFileReplacement, error) {
	rawKey := strings.TrimSpace(raw)
	value, err, _ := s.resolveGroup.Do(rawKey, func() (any, error) {
		return s.resolveFileURL(rawKey)
	})
	if err != nil {
		return oaiProxyFileReplacement{}, err
	}
	replacement, _ := value.(oaiProxyFileReplacement)
	return replacement, nil
}

func (s *oaiProxyFileStore) resolveFileURL(rawKey string) (oaiProxyFileReplacement, error) {
	now := time.Now()

	s.mu.Lock()
	cached, cachedOk := s.cache[rawKey]
	if cachedOk && now.Before(cached.ExpiresAt) {
		s.mu.Unlock()
		return cached.Replacement, nil
	}
	if cachedOk {
		s.cacheBytes -= cached.Size
		delete(s.cache, rawKey)
	}
	s.mu.Unlock()

	parsed, err := url.Parse(rawKey)
	if err != nil || !strings.EqualFold(parsed.Scheme, "file") ||
		(parsed.Host != "" && !strings.EqualFold(parsed.Host, "localhost")) ||
		parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}

	filePath := filepath.Clean(parsed.Path)
	if !filepath.IsAbs(filePath) || !oaiProxyPathWithin(s.rootPath, filePath) {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}

	relativePath, err := filepath.Rel(s.rootPath, filePath)
	if err != nil {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}

	root, err := os.OpenRoot(s.rootPath)
	if err != nil {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}
	defer root.Close()

	linkInfo, err := root.Lstat(relativePath)
	if err != nil || linkInfo.Mode()&os.ModeSymlink != 0 || !linkInfo.Mode().IsRegular() {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}

	file, err := root.Open(relativePath)
	if err != nil {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil || !info.Mode().IsRegular() || info.Size() > s.maxFileBytes {
		return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
	}

	var replacement oaiProxyFileReplacement
	if info.Size() <= s.inlineLimit {
		data, err := io.ReadAll(io.LimitReader(file, s.inlineLimit+1))
		if err != nil || int64(len(data)) > s.inlineLimit {
			return oaiProxyFileReplacement{}, fmt.Errorf("invalid file URL")
		}
		replacement = s.inlineReplacement(filePath, data)
	} else {
		replacement, err = s.spoolFile(file, filePath)
		if err != nil {
			return oaiProxyFileReplacement{}, err
		}
	}

	if err := root.Remove(relativePath); err != nil && !os.IsNotExist(err) {
		log.Warn("OAI proxy could not delete file URL output: path=%s err=%v", filePath, err)
	}

	s.cacheReplacement(rawKey, replacement)

	return replacement, nil
}

func (s *oaiProxyFileStore) cacheReplacement(raw string, replacement oaiProxyFileReplacement) {
	size := int64(len(replacement.DownloadURL) + len(replacement.DataURL) + len(replacement.Base64))
	if size > s.cacheMaxBytes {
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if existing, ok := s.cache[raw]; ok {
		s.cacheBytes -= existing.Size
		delete(s.cache, raw)
	}

	s.cache[raw] = oaiProxyCachedFile{
		Replacement: replacement,
		ExpiresAt:   time.Now().Add(s.ttl),
		Size:        size,
	}
	s.cacheBytes += size

	for s.cacheBytes > s.cacheMaxBytes {
		evicted := false
		for key, entry := range s.cache {
			if entry.Replacement.DownloadURL != "" {
				continue
			}
			delete(s.cache, key)
			s.cacheBytes -= entry.Size
			evicted = true
			break
		}
		if !evicted {
			break
		}
	}
}

func (s *oaiProxyFileStore) inlineReplacement(filePath string, data []byte) oaiProxyFileReplacement {
	contentType := oaiProxyDetectContentType(filePath, data)
	encoded := base64.StdEncoding.EncodeToString(data)
	dataURL := "data:" + strings.ReplaceAll(contentType, " ", "") + ";base64," + encoded

	if strings.HasPrefix(strings.ToLower(contentType), "image/") {
		return oaiProxyFileReplacement{
			DataURL: dataURL,
			Base64:  encoded,
			Image:   true,
		}
	}

	return oaiProxyFileReplacement{DataURL: dataURL}
}

func (s *oaiProxyFileStore) spoolFile(file *os.File, filePath string) (oaiProxyFileReplacement, error) {
	id, err := oaiProxyRandomToken()
	if err != nil {
		return oaiProxyFileReplacement{}, err
	}

	destinationPath := filepath.Join(s.spoolDir, id)
	destination, err := os.OpenFile(destinationPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return oaiProxyFileReplacement{}, err
	}

	cleanup := func() {
		_ = destination.Close()
		_ = os.Remove(destinationPath)
	}

	header := make([]byte, 512)
	n, readErr := io.ReadFull(file, header)
	if readErr != nil && !errors.Is(readErr, io.EOF) && !errors.Is(readErr, io.ErrUnexpectedEOF) {
		cleanup()
		return oaiProxyFileReplacement{}, readErr
	}
	header = header[:n]

	if n > 0 {
		if _, err := destination.Write(header); err != nil {
			cleanup()
			return oaiProxyFileReplacement{}, err
		}
	}

	copied, err := io.Copy(destination, io.LimitReader(file, s.maxFileBytes-int64(n)+1))
	if err != nil {
		cleanup()
		return oaiProxyFileReplacement{}, err
	}

	size := int64(n) + copied
	if size > s.maxFileBytes {
		cleanup()
		return oaiProxyFileReplacement{}, fmt.Errorf("file URL output is too large")
	}
	if err := destination.Close(); err != nil {
		_ = os.Remove(destinationPath)
		return oaiProxyFileReplacement{}, err
	}

	contentType := oaiProxyDetectContentType(filePath, header)
	entry := oaiProxySpoolEntry{
		Path:        destinationPath,
		Name:        filepath.Base(filePath),
		ContentType: contentType,
		ExpiresAt:   time.Now().Add(s.ttl),
	}

	s.mu.Lock()
	s.entries[id] = entry
	s.mu.Unlock()

	return oaiProxyFileReplacement{
		DownloadURL: s.publicURL.JoinPath("files", id).String(),
		Large:       true,
		Image:       strings.HasPrefix(strings.ToLower(contentType), "image/"),
	}, nil
}

func (s *oaiProxyFileStore) HandleDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	index := strings.LastIndex(r.URL.Path, "/files/")
	if index < 0 {
		http.NotFound(w, r)
		return
	}
	id := r.URL.Path[index+len("/files/"):]
	if id == "" || strings.Contains(id, "/") {
		http.NotFound(w, r)
		return
	}

	now := time.Now()
	var expiredPath string

	s.mu.Lock()
	entry, ok := s.entries[id]
	if ok && !now.Before(entry.ExpiresAt) {
		expiredPath = entry.Path
		delete(s.entries, id)
		ok = false
	}
	s.mu.Unlock()

	if expiredPath != "" {
		_ = os.Remove(expiredPath)
	}
	if !ok {
		http.NotFound(w, r)
		return
	}

	file, err := os.Open(entry.Path)
	if err != nil {
		s.mu.Lock()
		delete(s.entries, id)
		s.mu.Unlock()
		http.NotFound(w, r)
		return
	}
	defer file.Close()

	if entry.ContentType != "" {
		w.Header().Set("Content-Type", entry.ContentType)
	}
	w.Header().Set("X-Content-Type-Options", "nosniff")
	if entry.Name != "" {
		w.Header().Set("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{
			"filename": entry.Name,
		}))
	}

	http.ServeContent(w, r, entry.Name, time.Time{}, file)
}

func oaiProxyDetectContentType(filePath string, data []byte) string {
	contentType := http.DetectContentType(data)
	if contentType == "application/octet-stream" || strings.HasPrefix(contentType, "text/plain") {
		if extensionType := mime.TypeByExtension(filepath.Ext(filePath)); extensionType != "" {
			return extensionType
		}
	}
	return contentType
}

func oaiProxyPathWithin(root string, candidate string) bool {
	relative, err := filepath.Rel(filepath.Clean(root), filepath.Clean(candidate))
	if err != nil {
		return false
	}

	return relative != "." &&
		relative != ".." &&
		!strings.HasPrefix(relative, ".."+string(os.PathSeparator)) &&
		!filepath.IsAbs(relative)
}

func oaiProxyRandomToken() (string, error) {
	data := make([]byte, 32)
	if _, err := rand.Read(data); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(data), nil
}
