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
	Workspace string                        `json:"workspace,omitempty"`
	LastQuery InferencePlaygroundTokenUsage `json:"lastQuery"`
	Messages  []playgroundPersistedMessage  `json:"messages"`
}

type playgroundPersistedMessage struct {
	Role           string                      `json:"role"`
	Content        string                      `json:"content"`
	Synthetic      bool                        `json:"synthetic,omitempty"`
	Reasoning      string                      `json:"reasoning,omitempty"`
	ReasoningTitle string                      `json:"reasoningTitle,omitempty"`
	Parts          []playgroundChatMessagePart `json:"parts,omitempty"`
	GeneratedAt    string                      `json:"generatedAt,omitempty"`
	ModelName      string                      `json:"modelName,omitempty"`
	StartedAt      string                      `json:"startedAt,omitempty"`
	FirstTokenAt   string                      `json:"firstTokenAt,omitempty"`
	FinishedAt     string                      `json:"finishedAt,omitempty"`
	OutputTokens   int64                       `json:"outputTokens,omitempty"`
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

func playgroundThreadPersisted(thread playgroundChatThread) playgroundPersistedThread {
	messages := make([]playgroundPersistedMessage, 0, len(thread.Messages))
	for _, msg := range thread.Messages {
		messages = append(messages, playgroundPersistedMessage{
			Role:           msg.Role,
			Content:        msg.Content,
			Synthetic:      msg.Synthetic,
			Reasoning:      msg.Reasoning,
			ReasoningTitle: msg.ReasoningTitle,
			Parts:          msg.Parts,
			GeneratedAt:    playgroundFormatTime(msg.GeneratedAt),
			ModelName:      msg.ModelName,
			StartedAt:      playgroundFormatTime(msg.StartedAt),
			FirstTokenAt:   playgroundFormatTime(msg.FirstTokenAt),
			FinishedAt:     playgroundFormatTime(msg.FinishedAt),
			OutputTokens:   msg.OutputTokens,
		})
	}
	return playgroundPersistedThread{
		Version:   1,
		Id:        thread.Id,
		Title:     thread.Title,
		CreatedAt: playgroundFormatTime(thread.CreatedAt),
		UpdatedAt: playgroundFormatTime(thread.UpdatedAt),
		Usage:     thread.Usage,
		Workspace: strings.TrimSpace(thread.WorkspacePath),
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
			parts = playgroundChatMessageParts(msg.Content, msg.Reasoning, msg.ReasoningTitle, false)
		}
		messages = append(messages, playgroundChatMessage{
			Role:           msg.Role,
			Content:        msg.Content,
			Synthetic:      msg.Synthetic,
			Reasoning:      msg.Reasoning,
			ReasoningTitle: msg.ReasoningTitle,
			Parts:          parts,
			GeneratedAt:    generatedAt,
			ModelName:      msg.ModelName,
			StartedAt:      startedAt,
			FirstTokenAt:   firstTokenAt,
			FinishedAt:     finishedAt,
			OutputTokens:   msg.OutputTokens,
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
		Id:                     persisted.Id,
		Title:                  title,
		CreatedAt:              createdAt,
		UpdatedAt:              updatedAt,
		Usage:                  persisted.Usage,
		WorkspacePath:          strings.TrimSpace(persisted.Workspace),
		LastQuery:              lastQuery,
		Messages:               messages,
		TitleGenerated:         true,
		TitleGenerationStarted: true,
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
