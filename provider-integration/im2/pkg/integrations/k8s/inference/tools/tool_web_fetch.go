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
	"strings"
	"time"

	"github.com/mackee/go-readability"

	"golang.org/x/net/html/charset"
)

const webFetchResponseLimit = 5 * 1024 * 1024

const webFetchUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"

type webFetchPayload struct {
	URL        string `json:"url"`
	Format     string `json:"format"`
	TimeoutMs  int    `json:"timeout_ms"`
	MaxTokens  int    `json:"max_tokens"`
	Query      string `json:"query"`
	DocumentID string `json:"document_id"`
	Cursor     string `json:"cursor"`
}

func ToolWebFetch(payload string) {
	if err := webFetchRun(payload); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func webFetchRun(payload string) error {
	args := webFetchPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		return fmt.Errorf("tool arguments must be valid JSON")
	}
	return webFetchCached(args)
}

func webFetchDownload(args webFetchPayload) (webFetchDocument, error) {
	var document webFetchDocument
	args.URL = strings.TrimSpace(args.URL)
	args.Format = strings.ToLower(strings.TrimSpace(args.Format))
	if args.Format == "" {
		args.Format = "markdown"
	}
	if args.Format != "markdown" && args.Format != "html" {
		return document, fmt.Errorf("format must be markdown or html")
	}
	if args.TimeoutMs <= 0 {
		args.TimeoutMs = 15000
	}
	if args.TimeoutMs > 30000 {
		args.TimeoutMs = 30000
	}

	requestURL, err := url.Parse(args.URL)
	if err != nil || (requestURL.Scheme != "http" && requestURL.Scheme != "https") || requestURL.Host == "" {
		return document, fmt.Errorf("only http and https URLs are supported")
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(args.TimeoutMs)*time.Millisecond)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return document, err
	}
	request.Header.Set("Accept", webFetchAcceptHeader(args.Format))
	request.Header.Set("User-Agent", webFetchUserAgent)

	client := &http.Client{}
	response, err := client.Do(request)
	if err != nil {
		return document, fmt.Errorf("fetch failed: %w", err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		return document, fmt.Errorf("fetch failed: HTTP %d", response.StatusCode)
	}

	contentType, params, err := mime.ParseMediaType(response.Header.Get("Content-Type"))
	contentType = strings.ToLower(contentType)
	if err != nil || !webFetchTextualMime(contentType) {
		return document, fmt.Errorf("response content type is not textual: %s", response.Header.Get("Content-Type"))
	}

	extractHTML := args.Format == "markdown" && (contentType == "text/html" || contentType == "application/xhtml+xml")
	responseLimit := webFetchResponseLimit
	body, err := io.ReadAll(io.LimitReader(response.Body, int64(responseLimit+1)))
	if err != nil {
		return document, fmt.Errorf("fetch failed: %w", err)
	}
	responseTruncated := len(body) > responseLimit
	body = body[:min(len(body), responseLimit)]

	content, err := webFetchDecode(body, params["charset"])
	if err != nil {
		return document, err
	}
	if extractHTML {
		page, parseErr := readability.ParseHTML(content, response.Request.URL.String())
		if parseErr != nil {
			return document, fmt.Errorf("HTML parsing failed: %w", parseErr)
		}
		readability.PreprocessDocument(page)
		article := readability.ExtractContent(page, readability.DefaultOptions())
		content = readability.ToMarkdown(article.Root)
		if strings.TrimSpace(content) == "" {
			content = readability.ToMarkdown(page.Body)
		}
	}
	if strings.TrimSpace(content) == "" {
		return document, fmt.Errorf("fetch returned no readable content")
	}
	return webFetchDocument{
		RequestURL:        args.URL,
		URL:               response.Request.URL.String(),
		Status:            response.StatusCode,
		ContentType:       response.Header.Get("Content-Type"),
		Format:            args.Format,
		Content:           content,
		DownloadTruncated: responseTruncated,
		CreatedAt:         time.Now(),
	}, nil
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
