package inference

import (
	"encoding/json"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"time"

	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// Inference playground thread storage
// =====================================================================================================================
// This file stores playground threads as versioned JSON in the member filesystem. The files remain user-accessible, so
// users can inspect or delete their chat data without help from operators. The application layer in `playground.go`
// owns live state and calls this file only to load summaries or flush buffered changes.
//
// The store partitions files by UTC creation date. Date partitioning avoids one flat directory as history grows. A load
// retains the 30 most recently updated valid threads. This bound limits state held during startup and state sent to the
// UI. Writes use atomic replacement, and deletion uses the storage path retained when each thread was loaded.

// Persisted schema
// ---------------------------------------------------------------------------------------------------------------------
// Playground data lives below `Inference/Chats`. Reads reject files above the JSON size limit. The load limit applies
// after records are validated, deduplicated by thread ID, and sorted by update time.
//
// `playgroundPersistedThread` defines the versioned top-level record. `playgroundPersistedMessage` stores message text,
// parts, model details, timing, and output usage. Timestamps use strings in the persisted format, while application
// state uses Unix milliseconds. JSON omits optional message fields but always stores thread identity, usage, and history.

const (
	playgroundChatsSubPath      = "Inference/Chats"
	playgroundThreadLoadLimit   = 30
	playgroundThreadMaxJSONSize = 8 * 1024 * 1024
)

type playgroundPersistedThread struct {
	Version   int                           `json:"version"`
	Id        string                        `json:"id"`
	Title     string                        `json:"title"`
	CreatedAt string                        `json:"createdAt"`
	UpdatedAt string                        `json:"updatedAt"`
	Usage     InferencePlaygroundTokenUsage `json:"usage"`
	LastQuery InferencePlaygroundTokenUsage `json:"lastQuery"`
	Messages  []playgroundPersistedMessage  `json:"messages"`
}

type playgroundPersistedMessage struct {
	Role         string                      `json:"role"`
	Content      string                      `json:"content"`
	Synthetic    bool                        `json:"synthetic,omitempty"`
	Reasoning    string                      `json:"reasoning,omitempty"`
	Parts        []playgroundChatMessagePart `json:"parts,omitempty"`
	GeneratedAt  string                      `json:"generatedAt,omitempty"`
	ModelName    string                      `json:"modelName,omitempty"`
	StartedAt    string                      `json:"startedAt,omitempty"`
	FirstTokenAt string                      `json:"firstTokenAt,omitempty"`
	FinishedAt   string                      `json:"finishedAt,omitempty"`
	OutputTokens int64                       `json:"outputTokens,omitempty"`
}

// Public summaries and load
// ---------------------------------------------------------------------------------------------------------------------
// `InferencePlaygroundThreadSummaries` exposes the same retained thread set as lightweight API summaries.
// `inferencePlaygroundThreadsLoad` initializes the member filesystem, creates the chat root when needed, discovers JSON
// files, and ignores invalid or empty records. Empty records do not represent materialized chats.
//
// Loading keeps the newest record when more than one file contains the same thread ID. It then sorts all threads by
// update time and retains 30. This limit bounds both startup-held application state and the thread state sent to the UI.

func InferencePlaygroundThreadSummaries(owner string, project util.Option[string]) []orcapi.InferencePlaygroundThread {
	threads := inferencePlaygroundThreadsLoad(owner, project)
	result := make([]orcapi.InferencePlaygroundThread, 0, len(threads))
	for _, thread := range threads {
		result = append(result, orcapi.InferencePlaygroundThread{
			Id:        thread.Id,
			Title:     thread.Title,
			UpdatedAt: thread.UpdatedAt,
		})
	}
	return result
}

func inferencePlaygroundThreadsLoad(owner string, project util.Option[string]) []playgroundChatThread {
	basePath, _, err := filesystem.InitializeMemberFiles(owner, project)
	if err != nil {
		return nil
	}
	root := playgroundChatsRoot(basePath)
	if err := filesystem.DoCreateFolder(root); err != nil {
		return nil
	}

	paths := playgroundThreadPaths(root)
	threads := make([]playgroundChatThread, 0, len(paths))
	threadsById := map[string]playgroundChatThread{}
	for _, path := range paths {
		thread, ok := playgroundThreadRead(path)
		if !ok || len(thread.Messages) == 0 {
			continue
		}
		current, seen := threadsById[thread.Id]
		if seen && current.UpdatedAt >= thread.UpdatedAt {
			continue
		}
		threadsById[thread.Id] = thread
	}
	for _, thread := range threadsById {
		threads = append(threads, thread)
	}
	sort.SliceStable(threads, func(i, j int) bool {
		return threads[i].UpdatedAt > threads[j].UpdatedAt
	})
	if len(threads) > playgroundThreadLoadLimit {
		threads = threads[:playgroundThreadLoadLimit]
	}
	return threads
}

// Flush and write flow
// ---------------------------------------------------------------------------------------------------------------------
// `inferencePlaygroundThreadsFlush` returns immediately when no thread or deletion is dirty. The periodic caller in
// `playground.go` uses this batching to avoid synchronous filesystem work for every message or streaming event.
//
// A flush verifies filesystem access and rejects writes to a locked drive. It deletes requested paths, writes each
// nonempty dirty thread with atomic replacement, and removes an old file if the creation-based target path changed.
// Lazy thread materialization keeps empty threads out of this write path and therefore avoids empty files.

func inferencePlaygroundThreadsFlush(owner string, project util.Option[string], threads []playgroundChatThread, deletedThreadIds []string, deletedThreadPaths []string) bool {
	dirty := len(deletedThreadIds) > 0 || len(deletedThreadPaths) > 0
	for _, thread := range threads {
		if thread.Dirty {
			dirty = true
			break
		}
	}
	if !dirty {
		return true
	}

	basePath, drive, err := filesystem.InitializeMemberFiles(owner, project)
	if err != nil {
		return false
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return false
	}
	root := playgroundChatsRoot(basePath)
	if err := filesystem.DoCreateFolder(root); err != nil {
		return false
	}

	deleted := map[string]bool{}
	for _, path := range deletedThreadPaths {
		path = strings.TrimSpace(path)
		if path == "" || deleted[path] {
			continue
		}
		deleted[path] = true
		if info, statErr := filesystem.Stat(path); statErr == nil && !info.IsDir() {
			if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
				return false
			}
		}
	}

	for i := range threads {
		thread := &threads[i]
		if !thread.Dirty || len(thread.Messages) == 0 {
			continue
		}

		targetPath := playgroundThreadPath(root, *thread)
		data, jsonErr := json.MarshalIndent(playgroundThreadPersisted(*thread), "", "  ")
		if jsonErr != nil {
			return false
		}
		data = append(data, '\n')
		if writeErr := filesystem.WriteFileAtomic(targetPath, data, 0660); writeErr != nil {
			return false
		}

		if thread.StoragePath != "" && thread.StoragePath != targetPath {
			if info, statErr := filesystem.Stat(thread.StoragePath); statErr == nil && !info.IsDir() {
				if deleteErr := filesystem.DoDeleteFile(thread.StoragePath); deleteErr != nil {
					return false
				}
			}
		}
		thread.StoragePath = targetPath
	}

	return true
}

// Discovery and read
// ---------------------------------------------------------------------------------------------------------------------
// `playgroundThreadPaths` walks the expected year, month, and day directory levels in descending name order. It accepts
// visible JSON files only and skips unexpected files or directories. This focused discovery keeps unrelated member
// files outside the playground load path.
//
// `playgroundSortedChildren` provides stable traversal order, and `playgroundIsDir` validates every directory level.
// `playgroundThreadRead` enforces the size limit, decodes JSON, validates and converts the record, and records its exact
// storage path for later deletion or relocation.

func playgroundThreadPaths(root string) []string {
	years := playgroundSortedChildren(root, true)
	paths := []string{}
	for _, year := range years {
		yearPath := filepath.Join(root, year)
		if !playgroundIsDir(yearPath) {
			continue
		}
		months := playgroundSortedChildren(yearPath, true)
		for _, month := range months {
			monthPath := filepath.Join(yearPath, month)
			if !playgroundIsDir(monthPath) {
				continue
			}
			days := playgroundSortedChildren(monthPath, true)
			for _, day := range days {
				dayPath := filepath.Join(monthPath, day)
				if !playgroundIsDir(dayPath) {
					continue
				}
				files := playgroundSortedChildren(dayPath, true)
				for _, file := range files {
					if !strings.HasSuffix(file, ".json") || strings.HasPrefix(file, ".") {
						continue
					}
					path := filepath.Join(dayPath, file)
					if playgroundIsDir(path) {
						continue
					}
					paths = append(paths, path)
				}
			}
		}
	}
	return paths
}

func playgroundSortedChildren(path string, descending bool) []string {
	names, err := filesystem.ListDirNames(path)
	if err != nil {
		return nil
	}
	names = slices.DeleteFunc(names, func(name string) bool {
		return name == "." || name == ".."
	})
	sort.Strings(names)
	if descending {
		slices.Reverse(names)
	}
	return names
}

func playgroundIsDir(path string) bool {
	info, err := filesystem.Stat(path)
	return err == nil && info.IsDir()
}

func playgroundThreadRead(path string) (playgroundChatThread, bool) {
	data, err := filesystem.ReadFile(path, playgroundThreadMaxJSONSize)
	if err != nil {
		return playgroundChatThread{}, false
	}

	var persisted playgroundPersistedThread
	if jsonErr := json.Unmarshal(data, &persisted); jsonErr != nil {
		return playgroundChatThread{}, false
	}
	thread, ok := playgroundThreadFromPersisted(persisted)
	if !ok {
		return playgroundChatThread{}, false
	}
	thread.StoragePath = path
	return thread, true
}

// Conversion
// ---------------------------------------------------------------------------------------------------------------------
// `playgroundThreadPersisted` converts live messages and thread usage to version 1 JSON records. It formats each event
// time as UTC text and preserves message parts so the UI can restore attachments, reasoning, and tool output.
//
// `playgroundThreadFromPersisted` accepts version 1 records with an ID and valid thread creation and update times. It
// rebuilds presentation parts for older records that have only content and reasoning. Missing titles use `New thread`.
// Missing latest-query usage uses `playgroundPersistedLastQueryFallback`, which derives a bounded output value from the
// last assistant message and the cumulative usage record.

func playgroundThreadPersisted(thread playgroundChatThread) playgroundPersistedThread {
	messages := make([]playgroundPersistedMessage, 0, len(thread.Messages))
	for _, msg := range thread.Messages {
		messages = append(messages, playgroundPersistedMessage{
			Role:         msg.Role,
			Content:      msg.Content,
			Synthetic:    msg.Synthetic,
			Reasoning:    msg.Reasoning,
			Parts:        msg.Parts,
			GeneratedAt:  playgroundFormatTime(msg.GeneratedAt),
			ModelName:    msg.ModelName,
			StartedAt:    playgroundFormatTime(msg.StartedAt),
			FirstTokenAt: playgroundFormatTime(msg.FirstTokenAt),
			FinishedAt:   playgroundFormatTime(msg.FinishedAt),
			OutputTokens: msg.OutputTokens,
		})
	}
	return playgroundPersistedThread{
		Version:   1,
		Id:        thread.Id,
		Title:     thread.Title,
		CreatedAt: playgroundFormatTime(thread.CreatedAt),
		UpdatedAt: playgroundFormatTime(thread.UpdatedAt),
		Usage:     thread.Usage,
		LastQuery: thread.LastQuery,
		Messages:  messages,
	}
}

func playgroundThreadFromPersisted(persisted playgroundPersistedThread) (playgroundChatThread, bool) {
	if persisted.Version != 1 || strings.TrimSpace(persisted.Id) == "" {
		return playgroundChatThread{}, false
	}
	createdAt, ok := playgroundParseTime(persisted.CreatedAt)
	if !ok {
		return playgroundChatThread{}, false
	}
	updatedAt, ok := playgroundParseTime(persisted.UpdatedAt)
	if !ok {
		return playgroundChatThread{}, false
	}

	messages := make([]playgroundChatMessage, 0, len(persisted.Messages))
	for _, msg := range persisted.Messages {
		generatedAt, _ := playgroundParseTime(msg.GeneratedAt)
		startedAt, _ := playgroundParseTime(msg.StartedAt)
		firstTokenAt, _ := playgroundParseTime(msg.FirstTokenAt)
		finishedAt, _ := playgroundParseTime(msg.FinishedAt)
		parts := msg.Parts
		if len(parts) == 0 {
			parts = playgroundChatMessageParts(msg.Content, msg.Reasoning)
		}
		messages = append(messages, playgroundChatMessage{
			Role:         msg.Role,
			Content:      msg.Content,
			Synthetic:    msg.Synthetic,
			Reasoning:    msg.Reasoning,
			Parts:        parts,
			GeneratedAt:  generatedAt,
			ModelName:    msg.ModelName,
			StartedAt:    startedAt,
			FirstTokenAt: firstTokenAt,
			FinishedAt:   finishedAt,
			OutputTokens: msg.OutputTokens,
		})
	}

	title := strings.TrimSpace(persisted.Title)
	if title == "" {
		title = "New thread"
	}
	lastQuery := persisted.LastQuery
	if playgroundTokenUsageIsZero(lastQuery) {
		lastQuery = playgroundPersistedLastQueryFallback(persisted.Usage, messages)
	}
	return playgroundChatThread{
		Id:        persisted.Id,
		Title:     title,
		CreatedAt: createdAt,
		UpdatedAt: updatedAt,
		Usage:     persisted.Usage,
		LastQuery: lastQuery,
		Messages:  messages,
	}, true
}

func playgroundTokenUsageIsZero(usage InferencePlaygroundTokenUsage) bool {
	return usage.Input == 0 && usage.CachedInput == 0 && usage.Output == 0 && usage.Reported == 0
}

func playgroundPersistedLastQueryFallback(usage InferencePlaygroundTokenUsage, messages []playgroundChatMessage) InferencePlaygroundTokenUsage {
	lastAssistantOutput := int64(0)
	for i := len(messages) - 1; i >= 0; i-- {
		if messages[i].Role == "assistant" && messages[i].OutputTokens > 0 {
			lastAssistantOutput = messages[i].OutputTokens
			break
		}
	}
	if lastAssistantOutput == 0 || lastAssistantOutput > usage.Output {
		lastAssistantOutput = usage.Output
	}
	reported := usage.Input + usage.CachedInput + lastAssistantOutput
	if usage.Reported > 0 && reported == 0 {
		reported = usage.Reported
	}
	return InferencePlaygroundTokenUsage{
		Input:       usage.Input,
		CachedInput: usage.CachedInput,
		Output:      lastAssistantOutput,
		Reported:    reported,
	}
}

// Paths and time
// ---------------------------------------------------------------------------------------------------------------------
// `playgroundChatsRoot` joins the member base path with the playground subpath. `playgroundThreadPath` partitions each
// file by the UTC year, month, and day of thread creation. `playgroundThreadFileName` combines that UTC creation time
// with the thread ID to provide a readable and stable JSON filename.
//
// `playgroundThreadCreatedTime` prefers creation time, then update time, then the current time when neither exists.
// `playgroundFormatTime` writes UTC timestamps with millisecond precision and leaves zero values empty.
// `playgroundParseTime` accepts RFC 3339 values, including higher precision, and returns Unix milliseconds.

func playgroundChatsRoot(basePath string) string {
	return filepath.Join(basePath, playgroundChatsSubPath)
}

func playgroundThreadPath(root string, thread playgroundChatThread) string {
	createdAt := playgroundThreadCreatedTime(thread).UTC()
	return filepath.Join(
		root,
		createdAt.Format("2006"),
		createdAt.Format("01"),
		createdAt.Format("02"),
		playgroundThreadFileName(thread),
	)
}

func playgroundThreadFileName(thread playgroundChatThread) string {
	createdAt := playgroundThreadCreatedTime(thread).UTC()
	return createdAt.Format("20060102T150405.000Z") + "-" + thread.Id + ".json"
}

func playgroundThreadCreatedTime(thread playgroundChatThread) time.Time {
	if thread.CreatedAt > 0 {
		return time.UnixMilli(thread.CreatedAt)
	}
	if thread.UpdatedAt > 0 {
		return time.UnixMilli(thread.UpdatedAt)
	}
	return time.Now()
}

func playgroundFormatTime(ms int64) string {
	if ms == 0 {
		return ""
	}
	return time.UnixMilli(ms).UTC().Format("2006-01-02T15:04:05.000Z")
}

func playgroundParseTime(raw string) (int64, bool) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, false
	}
	parsed, err := time.Parse(time.RFC3339Nano, raw)
	if err != nil {
		return 0, false
	}
	return parsed.UnixMilli(), true
}
