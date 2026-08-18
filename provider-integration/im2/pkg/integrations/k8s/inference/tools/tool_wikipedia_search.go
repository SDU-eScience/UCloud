package tools

import (
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

type wikipediaSearchPayload struct {
	Query string `json:"query"`
	Limit int    `json:"limit"`
}

func ToolWikipediaSearch(payload string) {
	args := wikipediaSearchPayload{}
	if err := json.Unmarshal([]byte(payload), &args); err != nil {
		fmt.Fprintln(os.Stderr, "tool arguments must be valid JSON")
		return
	}
	args.Query = strings.TrimSpace(args.Query)
	if args.Query == "" {
		fmt.Fprintln(os.Stderr, "query must not be empty")
		return
	}
	if args.Limit <= 0 || args.Limit > 10 {
		args.Limit = 5
	}

	parameters := url.Values{
		"action":   {"query"},
		"list":     {"search"},
		"srsearch": {args.Query},
		"srlimit":  {fmt.Sprint(args.Limit)},
		"format":   {"json"},
		"origin":   {"*"},
	}
	request, err := http.NewRequest(http.MethodGet, "https://en.wikipedia.org/w/api.php?"+parameters.Encode(), nil)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return
	}
	request.Header.Set("User-Agent", webFetchUserAgent)

	response, err := (&http.Client{Timeout: 15 * time.Second}).Do(request)
	if err != nil {
		fmt.Fprintln(os.Stderr, "wikipedia search failed:", err)
		return
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		fmt.Fprintln(os.Stderr, "wikipedia search failed: HTTP", response.StatusCode)
		return
	}

	data, err := io.ReadAll(io.LimitReader(response.Body, 65536))
	if err != nil {
		fmt.Fprintln(os.Stderr, "wikipedia search failed:", err)
		return
	}
	parsed := struct {
		Query struct {
			Search []struct {
				Title   string `json:"title"`
				Snippet string `json:"snippet"`
			} `json:"search"`
		} `json:"query"`
	}{}
	if err := json.Unmarshal(data, &parsed); err != nil {
		fmt.Fprintln(os.Stderr, "wikipedia search failed:", err)
		return
	}

	results := make([]struct {
		Title   string `json:"title"`
		Snippet string `json:"snippet"`
		URL     string `json:"url"`
	}, 0, min(len(parsed.Query.Search), args.Limit))
	for _, item := range parsed.Query.Search[:min(len(parsed.Query.Search), args.Limit)] {
		snippet := html.UnescapeString(item.Snippet)
		snippet = strings.ReplaceAll(snippet, "<span class=\"searchmatch\">", "")
		snippet = strings.ReplaceAll(snippet, "</span>", "")
		snippet = strings.Join(strings.Fields(snippet), " ")
		snippet = wikipediaSearchTrim(snippet, 500)
		results = append(results, struct {
			Title   string `json:"title"`
			Snippet string `json:"snippet"`
			URL     string `json:"url"`
		}{
			Title:   item.Title,
			Snippet: snippet,
			URL:     "https://en.wikipedia.org/wiki/" + url.PathEscape(strings.ReplaceAll(item.Title, " ", "_")),
		})
	}

	result, _ := json.Marshal(struct {
		Query   string `json:"query"`
		Results any    `json:"results"`
		Count   int    `json:"count"`
	}{Query: args.Query, Results: results, Count: len(results)})
	fmt.Println(string(result))
}

func wikipediaSearchTrim(value string, limit int) string {
	runes := []rune(value)
	if len(runes) <= limit {
		return value
	}
	return string(runes[:limit])
}
