package tools

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/mackee/go-readability"

	"golang.org/x/net/html/charset"
	"ucloud.dk/shared/pkg/util"
)

const webFetchResponseLimit = 5 * 1024 * 1024
const webFetchContentLimit = 4 * 1024

const webFetchUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"

type webFetchPayload struct {
	URL       string `json:"url"`
	Format    string `json:"format"`
	TimeoutMs int    `json:"timeout_ms"`
}

func ToolWebFetch(payload string) {
	args := webFetchPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		fmt.Fprintln(os.Stderr, "tool arguments must be valid JSON")
		return
	}
	args.URL = strings.TrimSpace(args.URL)
	args.Format = strings.ToLower(strings.TrimSpace(args.Format))
	if args.Format == "" {
		args.Format = "markdown"
	}
	if args.Format != "markdown" && args.Format != "html" {
		fmt.Fprintln(os.Stderr, "format must be markdown or html")
		return
	}
	if args.TimeoutMs <= 0 {
		args.TimeoutMs = 15000
	}
	if args.TimeoutMs > 30000 {
		args.TimeoutMs = 30000
	}

	requestURL, err := url.Parse(args.URL)
	if err != nil || (requestURL.Scheme != "http" && requestURL.Scheme != "https") || requestURL.Host == "" {
		fmt.Fprintln(os.Stderr, "only http and https URLs are supported")
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(args.TimeoutMs)*time.Millisecond)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	request.Header.Set("Accept", webFetchAcceptHeader(args.Format))
	request.Header.Set("User-Agent", webFetchUserAgent)

	client := &http.Client{}
	response, err := client.Do(request)
	if err != nil {
		fmt.Fprintln(os.Stderr, "fetch failed:", err)
		return
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		fmt.Fprintln(os.Stderr, "fetch failed: HTTP", response.StatusCode)
		return
	}

	contentType, params, err := mime.ParseMediaType(response.Header.Get("Content-Type"))
	contentType = strings.ToLower(contentType)
	if err != nil || !webFetchTextualMime(contentType) {
		fmt.Fprintln(os.Stderr, "response content type is not textual:", response.Header.Get("Content-Type"))
		return
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, webFetchResponseLimit+1))
	if err != nil {
		fmt.Fprintln(os.Stderr, "fetch failed:", err)
		return
	}
	responseTruncated := len(body) > webFetchResponseLimit
	body = body[:min(len(body), webFetchResponseLimit)]

	tmpDir := ""
	truncatedFile := ""
	keepTmp := false
	defer func() {
		if tmpDir != "" && !keepTmp {
			_ = os.RemoveAll(tmpDir)
		}
	}()
	storeResponse := func() bool {
		if truncatedFile != "" {
			return true
		}
		var err error
		tmpRoot := filepath.Join(os.TempDir(), "ucloud-web-fetch")
		if err = os.MkdirAll(tmpRoot, 0700); err != nil {
			fmt.Fprintln(os.Stderr, err)
			return false
		}
		tmpDir = filepath.Join(tmpRoot, util.SecureToken())
		if err = os.Mkdir(tmpDir, 0700); err != nil {
			fmt.Fprintln(os.Stderr, err)
			return false
		}
		extensions, _ := mime.ExtensionsByType(contentType)
		extension := ".txt"
		if len(extensions) > 0 {
			extension = extensions[0]
		}
		truncatedFile = filepath.Join(tmpDir, "response"+extension)
		if err := os.WriteFile(truncatedFile, body, 0600); err != nil {
			fmt.Fprintln(os.Stderr, err)
			return false
		}
		return true
	}

	content := ""
	contentTruncated := false
	switch args.Format {
	case "markdown":
		content, err = webFetchDecode(body, params["charset"])
		if err != nil {
			_, _ = fmt.Fprintln(os.Stderr, err.Error())
			return
		}

		article, articleErr := readability.Extract(content, readability.DefaultOptions())
		if articleErr != nil {
			_, _ = fmt.Fprintln(os.Stderr, articleErr.Error())
			return
		}

		content = readability.ToMarkdown(article.Root)
		body = []byte(content)
		contentType = "text/markdown"
		if len(content) > webFetchContentLimit {
			content = strings.ToValidUTF8(content[:webFetchContentLimit], "\uFFFD")
			contentTruncated = true
		}
	case "html":
		content, err = webFetchDecode(body, params["charset"])
		if err == nil && len(content) > webFetchContentLimit {
			content = strings.ToValidUTF8(content[:webFetchContentLimit], "\uFFFD")
			contentTruncated = true
		}
	}
	if err != nil {
		_, _ = fmt.Fprintln(os.Stderr, err)
		return
	}

	truncated := responseTruncated || contentTruncated
	outputFile := ""
	if truncated {
		if !storeResponse() {
			return
		}
		keepTmp = true
		outputFile = truncatedFile
	}

	result, _ := json.Marshal(struct {
		URL           string `json:"url"`
		Status        int    `json:"status"`
		ContentType   string `json:"content_type"`
		Format        string `json:"format"`
		Content       string `json:"content"`
		Truncated     bool   `json:"truncated"`
		TruncatedFile string `json:"truncated_file,omitempty"`
	}{
		URL:           response.Request.URL.String(),
		Status:        response.StatusCode,
		ContentType:   response.Header.Get("Content-Type"),
		Format:        args.Format,
		Content:       content,
		Truncated:     truncated,
		TruncatedFile: outputFile,
	})
	fmt.Println(string(result))
}

func webFetchAcceptHeader(format string) string {
	switch format {
	case "html":
		return "text/html;q=1.0, application/xhtml+xml;q=0.9, text/plain;q=0.8, text/markdown;q=0.7, */*;q=0.1"
	default:
		return "text/markdown;q=1.0, text/x-markdown;q=0.9, text/plain;q=0.8, text/html;q=0.7, */*;q=0.1"
	}
}

func webFetchTextualMime(contentType string) bool {
	if strings.HasPrefix(contentType, "text/") {
		return true
	}
	if strings.HasSuffix(contentType, "+json") || strings.HasSuffix(contentType, "+xml") {
		return true
	}
	switch contentType {
	case "application/json", "application/xml", "application/xhtml+xml", "application/javascript", "application/x-javascript", "application/yaml", "application/x-yaml":
		return true
	default:
		return false
	}
}

func webFetchDecode(body []byte, encoding string) (string, error) {
	if encoding == "" || strings.EqualFold(encoding, "utf-8") || strings.EqualFold(encoding, "utf8") {
		return strings.ToValidUTF8(string(body), "\uFFFD"), nil
	}
	reader, err := charset.NewReader(bytes.NewReader(body), encoding)
	if err != nil {
		return "", err
	}
	decoded, err := io.ReadAll(reader)
	return strings.ToValidUTF8(string(decoded), "\uFFFD"), err
}
