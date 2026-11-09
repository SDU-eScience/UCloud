package tools

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"
)

const WebFetchDefaultTokens = 8000
const WebFetchMaxTokens = 24000

type webFetchBlock struct {
	Text    string
	Heading string
}

func WebFetchEstimateTokens(text string) int {
	count, run := 0, 0
	for _, r := range text {
		if r < 128 && (unicode.IsLetter(r) || unicode.IsDigit(r)) {
			run++
			continue
		}
		count += (run+2)/3 + utf8.RuneLen(r)
		run = 0
	}
	return count + (run+2)/3
}

func webFetchDigest(text string) string {
	digest := sha256.Sum256([]byte(text))
	return hex.EncodeToString(digest[:])
}

func webFetchBlocks(content string) []webFetchBlock {
	var blocks []webFetchBlock
	var lines []string
	heading, fence := "", ""
	flush := func() {
		text := strings.TrimSpace(strings.Join(lines, "\n"))
		if text != "" {
			blocks = append(blocks, webFetchBlock{Text: text, Heading: heading})
		}
		lines = nil
	}
	for _, line := range strings.Split(content, "\n") {
		trimmed := strings.TrimSpace(line)
		if fence == "" && strings.HasPrefix(trimmed, "#") {
			flush()
			heading = trimmed
		}
		if strings.HasPrefix(trimmed, "```") || strings.HasPrefix(trimmed, "~~~") {
			marker := trimmed[:3]
			if fence == "" {
				fence = marker
			} else if fence == marker {
				fence = ""
			}
		}
		if trimmed == "" && fence == "" {
			flush()
		} else {
			lines = append(lines, line)
		}
	}
	flush()
	return blocks
}

func webFetchSelect(blocks []webFetchBlock, query string) ([]webFetchBlock, bool) {
	terms := strings.FieldsFunc(strings.ToLower(query), func(r rune) bool { return !unicode.IsLetter(r) && !unicode.IsDigit(r) })
	if len(terms) == 0 {
		return blocks, false
	}
	type match struct{ Index, Score int }
	var matches []match
	for i, block := range blocks {
		text, heading := strings.ToLower(block.Text), strings.ToLower(block.Heading)
		score := 0
		for _, term := range terms {
			if strings.Contains(text, term) {
				score++
			}
			if strings.Contains(heading, term) {
				score += 3
			}
		}
		if score > 0 {
			matches = append(matches, match{i, score})
		}
	}
	if len(matches) == 0 {
		return blocks, false
	}
	sort.SliceStable(matches, func(i, j int) bool { return matches[i].Score > matches[j].Score })
	seen := make(map[int]bool)
	var selected []webFetchBlock
	for _, match := range matches {
		for i := max(0, match.Index-1); i <= min(len(blocks)-1, match.Index+1); i++ {
			if !seen[i] {
				selected = append(selected, blocks[i])
				seen[i] = true
			}
		}
	}
	return selected, true
}

func webFetchPage(document webFetchDocument, args webFetchPayload) (map[string]any, error) {
	budget := args.MaxTokens
	if budget <= 0 {
		budget = WebFetchDefaultTokens
	}
	budget = min(budget, WebFetchMaxTokens)
	blocks, matched := webFetchSelect(webFetchBlocks(document.Content), args.Query)
	index, offset := 0, 0
	identity := document.ID + ":" + webFetchDigest(args.Query)
	if args.Cursor != "" {
		parts := strings.Split(args.Cursor, ":")
		if len(parts) != 4 || strings.Join(parts[:2], ":") != identity {
			return nil, fmt.Errorf("cursor does not match the document and query")
		}
		var err error
		index, err = strconv.Atoi(parts[2])
		if err != nil {
			return nil, fmt.Errorf("invalid cursor")
		}
		offset, err = strconv.Atoi(parts[3])
		if err != nil || index < 0 || index >= len(blocks) || offset < 0 || offset >= len(blocks[index].Text) || !utf8.RuneStart(blocks[index].Text[offset]) {
			return nil, fmt.Errorf("invalid cursor")
		}
	}
	var content strings.Builder
	used := 0
	partial := false
	lastHeading := ""
	for index < len(blocks) {
		block := blocks[index]
		prefix := ""
		if content.Len() > 0 {
			prefix = "\n\n"
		}
		if block.Heading != "" && block.Heading != lastHeading && !strings.HasPrefix(block.Text[offset:], block.Heading) {
			prefix += block.Heading + "\n\n"
		}
		text := block.Text[offset:]
		cost := WebFetchEstimateTokens(prefix + text)
		if used+cost > budget {
			if content.Len() > 0 {
				break
			}
			prefixCost := WebFetchEstimateTokens(prefix)
			if prefixCost >= budget {
				prefix = ""
				prefixCost = 0
			}
			end := webFetchPrefix(text, budget-prefixCost)
			if end == 0 {
				return nil, fmt.Errorf("max_tokens is too small to return content")
			}
			content.WriteString(prefix)
			content.WriteString(text[:end])
			offset += end
			if offset == len(block.Text) {
				index++
				offset = 0
			} else {
				partial = true
			}
			break
		}
		content.WriteString(prefix)
		content.WriteString(text)
		used += cost
		lastHeading = block.Heading
		index++
		offset = 0
	}
	var headings []string
	seen := map[string]bool{}
	for _, block := range blocks[index:] {
		if block.Heading != "" && !seen[block.Heading] && len(headings) < 30 {
			heading := []rune(block.Heading)
			if len(heading) > 200 {
				heading = append(heading[:200], '…')
			}
			headings = append(headings, string(heading))
			seen[block.Heading] = true
		}
	}
	cursor := ""
	if index < len(blocks) {
		cursor = fmt.Sprintf("%s:%d:%d", identity, index, offset)
	}
	selectedLength := 0
	for _, block := range blocks {
		selectedLength += utf8.RuneCountInString(block.Text)
	}
	remainingLength := 0
	for i := index; i < len(blocks); i++ {
		text := blocks[i].Text
		if i == index {
			text = text[offset:]
		}
		remainingLength += utf8.RuneCountInString(text)
	}
	return map[string]any{
		"url": document.URL, "status": document.Status, "content_type": document.ContentType,
		"format": document.Format, "content": content.String(), "document_id": document.ID,
		"next_cursor": cursor, "truncated": cursor != "" || document.DownloadTruncated,
		"download_truncated": document.DownloadTruncated, "partial_block": partial,
		"returned_length":  utf8.RuneCountInString(content.String()),
		"available_length": utf8.RuneCountInString(document.Content), "selected_length": selectedLength, "remaining_length": remainingLength,
		"estimated_tokens": WebFetchEstimateTokens(content.String()), "token_count_is_estimate": true,
		"remaining_headings": headings, "query": args.Query, "query_matched": matched,
		"expires_at": document.CreatedAt.Add(webFetchCacheLifetime).Format("2006-01-02T15:04:05Z07:00"),
	}, nil
}

func webFetchPrefix(text string, budget int) int {
	end, cost, run, boundary := 0, 0, 0, 0
	for i, r := range text {
		nextRun := 0
		nextCost := cost
		if r < 128 && (unicode.IsLetter(r) || unicode.IsDigit(r)) {
			nextRun = run + 1
		} else {
			nextCost += (run+2)/3 + utf8.RuneLen(r)
		}
		if nextCost+(nextRun+2)/3 > budget {
			break
		}
		cost, run = nextCost, nextRun
		end = i + utf8.RuneLen(r)
		if r == '\n' || (r == '.' && end < len(text) && text[end] == ' ') {
			boundary = end
		}
	}
	if boundary > end/2 {
		return boundary
	}
	return end
}
