package oai_proxy

import (
	"bufio"
	"bytes"
	"compress/gzip"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
)

type oaiProxyRequestPathKey struct{}

type oaiProxyRewriter struct {
	files         *oaiProxyFileStore
	cache         map[string]oaiProxyFileReplacement
	allowAny      bool
	imageResponse bool
}

func oaiProxyNewRewriter(files *oaiProxyFileStore, allowAny bool, imageResponse bool) *oaiProxyRewriter {
	return &oaiProxyRewriter{
		files:         files,
		cache:         map[string]oaiProxyFileReplacement{},
		allowAny:      allowAny,
		imageResponse: imageResponse,
	}
}

func oaiProxyRewriteResponse(resp *http.Response, files *oaiProxyFileStore, maxJSONResponseBytes int64) error {
	if resp.Body == nil {
		return nil
	}
	if resp.Request != nil && resp.Request.Method == http.MethodHead {
		return nil
	}

	path := ""
	if resp.Request != nil {
		path, _ = resp.Request.Context().Value(oaiProxyRequestPathKey{}).(string)
		if path == "" {
			path = resp.Request.URL.Path
		}
	}
	allowAny := oaiProxyArtifactEndpoint(path)
	imageResponse := oaiProxyImageEndpoint(path)

	contentType := strings.ToLower(strings.TrimSpace(resp.Header.Get("Content-Type")))
	if strings.HasPrefix(contentType, "text/event-stream") {
		if err := oaiProxyDecodeContentEncoding(resp); err != nil {
			return err
		}

		resp.Body = &oaiProxySSEBody{
			body:          resp.Body,
			reader:        bufio.NewReader(resp.Body),
			rewriter:      oaiProxyNewRewriter(files, allowAny, imageResponse),
			maxEventBytes: maxJSONResponseBytes,
		}
		resp.ContentLength = -1
		resp.Header.Del("Content-Length")
		resp.Header.Del("Content-Encoding")
		return nil
	}

	if !strings.Contains(contentType, "json") {
		return nil
	}
	if err := oaiProxyDecodeContentEncoding(resp); err != nil {
		return err
	}

	data, err := io.ReadAll(io.LimitReader(resp.Body, maxJSONResponseBytes+1))
	if err != nil {
		return err
	}
	if int64(len(data)) > maxJSONResponseBytes {
		return fmt.Errorf("upstream JSON response is too large")
	}
	_ = resp.Body.Close()

	output := data
	if oaiProxyContainsFileScheme(data) {
		output, err = oaiProxyRewriteJSON(data, oaiProxyNewRewriter(files, allowAny, imageResponse))
		if err != nil {
			return err
		}
	}

	resp.Body = io.NopCloser(bytes.NewReader(output))
	resp.ContentLength = int64(len(output))
	resp.Header.Set("Content-Length", strconv.FormatInt(int64(len(output)), 10))
	resp.Header.Del("Content-Encoding")
	return nil
}

func oaiProxyArtifactEndpoint(path string) bool {
	return path == "/v1/images" ||
		strings.HasPrefix(path, "/v1/images/") ||
		path == "/v1/videos" ||
		strings.HasPrefix(path, "/v1/videos/") ||
		path == "/v1/responses" ||
		strings.HasPrefix(path, "/v1/responses/")
}

func oaiProxyImageEndpoint(path string) bool {
	return path == "/v1/images" || strings.HasPrefix(path, "/v1/images/")
}

func oaiProxyArtifactKey(key string) bool {
	switch strings.ToLower(key) {
	case "url", "file_url", "download_url", "content_url", "image_url", "audio_url", "video_url",
		"thumbnail_url", "preview_url":
		return true
	default:
		return false
	}
}

func oaiProxyDecodeContentEncoding(resp *http.Response) error {
	encoding := strings.TrimSpace(resp.Header.Get("Content-Encoding"))
	if encoding == "" || strings.EqualFold(encoding, "identity") {
		return nil
	}
	if !strings.EqualFold(encoding, "gzip") {
		return fmt.Errorf("unsupported upstream content encoding %q", encoding)
	}

	reader, err := gzip.NewReader(resp.Body)
	if err != nil {
		return err
	}
	resp.Body = &oaiProxyReadCloser{
		Reader:  reader,
		closers: []io.Closer{reader, resp.Body},
	}
	resp.Header.Del("Content-Encoding")
	resp.Header.Del("Content-Length")
	resp.ContentLength = -1
	return nil
}

type oaiProxyReadCloser struct {
	io.Reader
	closers []io.Closer
}

func (c *oaiProxyReadCloser) Close() error {
	var result error
	for _, closer := range c.closers {
		if err := closer.Close(); err != nil && result == nil {
			result = err
		}
	}
	return result
}

func oaiProxyRewriteJSON(data []byte, rewriter *oaiProxyRewriter) ([]byte, error) {
	value, err := oaiProxyDecodeJSON(data)
	if err != nil {
		return nil, err
	}

	changed, err := rewriter.rewriteValue(value, rewriter.allowAny)
	if err != nil {
		return nil, err
	}
	if !changed {
		return data, nil
	}
	return oaiProxyMarshalJSON(value)
}

func oaiProxyDecodeJSON(data []byte) (any, error) {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()

	var value any
	if err := decoder.Decode(&value); err != nil {
		return nil, err
	}

	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		return nil, fmt.Errorf("invalid JSON")
	}
	return value, nil
}

func oaiProxyMarshalJSON(value any) ([]byte, error) {
	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return bytes.TrimSuffix(buffer.Bytes(), []byte("\n")), nil
}

func (r *oaiProxyRewriter) rewriteValue(value any, allowFileURL bool) (bool, error) {
	switch typed := value.(type) {
	case map[string]any:
		changed := false
		for key, child := range typed {
			childAllow := allowFileURL || r.allowAny || oaiProxyArtifactKey(key)
			if text, ok := child.(string); ok && childAllow && oaiProxyIsFileURL(text) {
				replacement, err := r.replacement(text)
				if err != nil {
					return false, err
				}

				if replacement.Large {
					typed[key] = replacement.DownloadURL
				} else if replacement.Image && r.imageResponse && key == "url" {
					typed["b64_json"] = replacement.Base64
					delete(typed, key)
				} else {
					typed[key] = replacement.DataURL
				}

				changed = true
				continue
			}

			childChanged, err := r.rewriteValue(child, childAllow)
			if err != nil {
				return false, err
			}
			changed = changed || childChanged
		}
		return changed, nil

	case []any:
		changed := false
		for index, child := range typed {
			if text, ok := child.(string); ok && allowFileURL && oaiProxyIsFileURL(text) {
				replacement, err := r.replacement(text)
				if err != nil {
					return false, err
				}

				if replacement.Large {
					typed[index] = replacement.DownloadURL
				} else {
					typed[index] = replacement.DataURL
				}

				changed = true
				continue
			}

			childChanged, err := r.rewriteValue(child, allowFileURL)
			if err != nil {
				return false, err
			}
			changed = changed || childChanged
		}
		return changed, nil
	}

	return false, nil
}

func (r *oaiProxyRewriter) replacement(raw string) (oaiProxyFileReplacement, error) {
	raw = strings.TrimSpace(raw)
	if cached, ok := r.cache[raw]; ok {
		return cached, nil
	}

	replacement, err := r.files.ResolveFileURL(raw)
	if err != nil {
		return oaiProxyFileReplacement{}, err
	}
	r.cache[raw] = replacement
	return replacement, nil
}

func oaiProxyIsFileURL(raw string) bool {
	raw = strings.ToLower(strings.TrimSpace(raw))
	return strings.HasPrefix(raw, "file://") || strings.HasPrefix(raw, "file:/")
}

type oaiProxySSEBody struct {
	body          io.ReadCloser
	reader        *bufio.Reader
	rewriter      *oaiProxyRewriter
	maxEventBytes int64
	pending       []byte
	err           error
}

func (b *oaiProxySSEBody) Read(p []byte) (int, error) {
	for len(b.pending) == 0 && b.err == nil {
		event, readErr := b.readEvent()
		if len(event) > 0 {
			rewritten, err := oaiProxyRewriteSSEEvent(event, b.rewriter)
			if err != nil {
				b.err = err
				break
			}
			b.pending = rewritten
		}
		if readErr != nil {
			b.err = readErr
		}
	}

	if len(b.pending) == 0 {
		if b.err == nil {
			b.err = io.EOF
		}
		return 0, b.err
	}

	n := copy(p, b.pending)
	b.pending = b.pending[n:]
	return n, nil
}

func (b *oaiProxySSEBody) Close() error {
	return b.body.Close()
}

func (b *oaiProxySSEBody) readEvent() ([]byte, error) {
	var event bytes.Buffer

	for {
		line, err := b.reader.ReadString('\n')
		if len(line) > 0 {
			event.WriteString(line)
			if int64(event.Len()) > b.maxEventBytes {
				return nil, fmt.Errorf("upstream SSE event is too large")
			}
			if oaiProxySSELineContent(line) == "" {
				return event.Bytes(), nil
			}
		}

		if err != nil {
			if event.Len() > 0 {
				return event.Bytes(), err
			}
			return nil, err
		}
	}
}

func oaiProxyRewriteSSEEvent(event []byte, rewriter *oaiProxyRewriter) ([]byte, error) {
	text := string(event)
	if strings.TrimSpace(text) == "" || !oaiProxyContainsFileScheme(event) {
		return event, nil
	}

	lines := oaiProxySSESplitLines(text)
	var dataValues []string
	for _, line := range lines {
		content := oaiProxySSELineContent(line)
		if strings.HasPrefix(content, "data:") {
			value := strings.TrimPrefix(content, "data:")
			if strings.HasPrefix(value, " ") {
				value = value[1:]
			}
			dataValues = append(dataValues, value)
		}
	}

	payload := strings.Join(dataValues, "\n")
	if strings.TrimSpace(payload) == "" || strings.TrimSpace(payload) == "[DONE]" {
		return event, nil
	}

	value, err := oaiProxyDecodeJSON([]byte(payload))
	if err != nil {
		return event, nil
	}

	changed, err := rewriter.rewriteValue(value, rewriter.allowAny)
	if err != nil {
		return nil, err
	}
	if !changed {
		return event, nil
	}

	encoded, err := oaiProxyMarshalJSON(value)
	if err != nil {
		return nil, err
	}

	var output strings.Builder
	dataWritten := false
	for _, line := range lines {
		content := oaiProxySSELineContent(line)
		if strings.HasPrefix(content, "data:") {
			if !dataWritten {
				output.WriteString("data: ")
				output.Write(encoded)
				output.WriteByte('\n')
				dataWritten = true
			}
			continue
		}
		if content == "" {
			continue
		}

		output.WriteString(line)
		if !strings.HasSuffix(line, "\n") {
			output.WriteByte('\n')
		}
	}

	if !dataWritten {
		output.WriteString("data: ")
		output.Write(encoded)
		output.WriteByte('\n')
	}
	output.WriteByte('\n')

	return []byte(output.String()), nil
}

func oaiProxySSESplitLines(text string) []string {
	var lines []string
	for len(text) > 0 {
		index := strings.IndexByte(text, '\n')
		if index < 0 {
			lines = append(lines, text)
			break
		}
		lines = append(lines, text[:index+1])
		text = text[index+1:]
	}
	return lines
}

func oaiProxySSELineContent(line string) string {
	line = strings.TrimSuffix(line, "\n")
	line = strings.TrimSuffix(line, "\r")
	return line
}

func oaiProxyContainsFileScheme(data []byte) bool {
	for index := 0; index+5 <= len(data); index++ {
		if !(data[index] == 'f' || data[index] == 'F') {
			continue
		}
		if !(data[index+1] == 'i' || data[index+1] == 'I') {
			continue
		}
		if !(data[index+2] == 'l' || data[index+2] == 'L') {
			continue
		}
		if !(data[index+3] == 'e' || data[index+3] == 'E') {
			continue
		}
		if data[index+4] == ':' {
			return true
		}
	}
	return false
}
