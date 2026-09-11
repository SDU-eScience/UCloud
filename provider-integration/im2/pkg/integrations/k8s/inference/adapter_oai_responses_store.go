package inference

import (
	"encoding/json"
	"path/filepath"
	"strings"
	"sync"
	"time"

	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// Responses API state store
// =====================================================================================================================
// The Responses API requires server-side state for response lookup, background polling, conversations, and chained
// requests. This file provides that state with persisted JSON files and a short-lived, bounded memory cache.
//
// Persisted state lives in member files owned by the requesting user. User wallet state uses the user's home drive.
// Project wallet state uses that user's member files drive for the project. The file layout is:
//
//     <member-files>/Inference/Resources/
//         Conversations/<convId>.json
//         Responses/<respId>.json
//
// Each turn rewrites its conversation file. Each stored response uses a separate response file. A persisted response
// restores a memory cache miss and provides the conversation position needed for previous_response_id. Files become
// eligible for deletion after 30 days. The drive scan removes eligible response and conversation files.
//
// The memory cache speeds access to recent responses. It also temporarily supports store: false because every response
// enters memory, while only responses with Store set enter a response file. Such responses cannot be restored after
// cache expiry or process loss. Conversation files are still written when a turn supplies conversation state.

// State model and policy
// ---------------------------------------------------------------------------------------------------------------------
// The constants define the storage path, 30-day file retention, JSON read limit, cache lifetime, and cache targets. The
// normal write path removes the oldest owner entry at the owner target. It removes the oldest global entry at the global
// target. A response restored from a persisted file enters memory directly, so these values do not form strict bounds.
//
// inferenceStoredResponse keeps the response, owner, conversation ID, and position after that response. Persisted
// records include a format version and owner reference. The position lets chain resolution select the exact prefix for
// a prior response, including a response that is not the latest conversation turn.
//
// inferenceResponseGlobals contains the process-local response cache. Its mutex protects every cache read and write.

const (
	inferenceResponseResourcesSubPath        = "Inference/Resources"
	inferenceResponseRetention               = 30 * 24 * time.Hour
	inferenceResponseMaxJSONSize             = 8 * 1024 * 1024
	inferenceResponseStoreTTL                = 30 * time.Minute
	inferenceResponseStoreMaxEntries         = 256
	inferenceResponseStoreMaxEntriesPerOwner = 16
)

type inferenceStoredResponse struct {
	Response     OaiResponse
	Owner        string
	Conversation string
	ContextAfter int
	CreatedAt    time.Time
}

type inferencePersistedConversation struct {
	Version   int               `json:"version"`
	Id        string            `json:"id"`
	Owner     string            `json:"owner"`
	CreatedAt int64             `json:"createdAt"`
	UpdatedAt int64             `json:"updatedAt"`
	Items     []json.RawMessage `json:"items"`
}

type inferencePersistedResponse struct {
	Version      int         `json:"version"`
	Id           string      `json:"id"`
	Owner        string      `json:"owner"`
	Conversation string      `json:"conversation"`
	ContextAfter int         `json:"contextAfter"`
	Response     OaiResponse `json:"response"`
}

var inferenceResponseGlobals = struct {
	Mu        sync.RWMutex
	Responses map[string]inferenceStoredResponse
}{
	Responses: map[string]inferenceStoredResponse{},
}

// High-level combined get/set/lookup flow
// ---------------------------------------------------------------------------------------------------------------------
// inferenceResponseStoreSet is the write entry point. It expires old cache data, applies per-owner and global bounds,
// and always caches the response. It then writes conversation state when present. It writes a response file only when
// response.Store is true. This order means a persistence error can occur after the response entered memory.
//
// inferenceResponseStoreGet is the response lookup entry point. It accepts a current cache entry only for the same
// wallet owner. On a miss or expiry, it reads the response file and restores the cache. A persisted background response
// left queued or in progress across a server restart cannot complete. After its request timeout, lookup marks it failed,
// stores that state, and returns the failed response.
//
// inferenceResponseStoreLookupRecord supplies chain resolution with both response data and its conversation position.
// It checks memory first and then the response file. Both paths verify the wallet owner. A store: false response remains
// available for chaining only while its cache entry exists. A cached record without conversation data cannot form a
// chain and falls through to persisted lookup.

func inferenceResponseStoreSet(owner apm.WalletOwner, username string, response OaiResponse, conversation *inferencePersistedConversation) *util.HttpError {
	inferenceResponseGlobals.Mu.Lock()
	inferenceResponseStoreCleanLocked()
	ownerRef := owner.Reference()
	oldestOwnerId := ""
	oldestOwnerTime := time.Now()
	ownerEntries := 0
	oldestId := ""
	oldestTime := time.Now()
	for id, stored := range inferenceResponseGlobals.Responses {
		if stored.CreatedAt.Before(oldestTime) {
			oldestId = id
			oldestTime = stored.CreatedAt
		}
		if stored.Owner == ownerRef {
			ownerEntries++
			if stored.CreatedAt.Before(oldestOwnerTime) {
				oldestOwnerId = id
				oldestOwnerTime = stored.CreatedAt
			}
		}
	}
	if _, replacing := inferenceResponseGlobals.Responses[response.Id]; !replacing {
		if ownerEntries >= inferenceResponseStoreMaxEntriesPerOwner && oldestOwnerId != "" {
			delete(inferenceResponseGlobals.Responses, oldestOwnerId)
		} else if len(inferenceResponseGlobals.Responses) >= inferenceResponseStoreMaxEntries && oldestId != "" {
			delete(inferenceResponseGlobals.Responses, oldestId)
		}
	}
	inferenceResponseGlobals.Responses[response.Id] = inferenceStoredResponse{
		Response:     response,
		Owner:        ownerRef,
		Conversation: conversationIdOf(conversation),
		ContextAfter: contextAfterOf(conversation),
		CreatedAt:    time.Now(),
	}
	inferenceResponseGlobals.Mu.Unlock()

	if username == "" {
		return nil
	}

	if conversation != nil {
		if err := inferenceResponseStoreConversationWrite(owner, username, *conversation); err != nil {
			return err
		}
	}

	if response.Store {
		record := inferencePersistedResponse{
			Version:      1,
			Id:           response.Id,
			Owner:        owner.Reference(),
			Conversation: conversationIdOf(conversation),
			ContextAfter: contextAfterOf(conversation),
			Response:     response,
		}
		if err := inferenceResponseStoreWrite(owner, username, record); err != nil {
			return err
		}
	}
	return nil
}

func inferenceResponseStoreGet(owner apm.WalletOwner, username string, responseId string) (OaiResponse, bool) {
	inferenceResponseGlobals.Mu.RLock()
	stored, ok := inferenceResponseGlobals.Responses[responseId]
	inferenceResponseGlobals.Mu.RUnlock()

	if !ok || stored.Owner != owner.Reference() {
		ok = false
	}
	if ok && time.Since(stored.CreatedAt) > inferenceResponseStoreTTL {
		inferenceResponseStoreMemoryEvict(responseId)
		ok = false
	}
	if ok {
		return stored.Response, true
	}

	record, ok := inferenceResponseStoreRead(owner, username, responseId)
	if !ok {
		return OaiResponse{}, false
	}

	if record.Response.Background && (record.Response.Status == "queued" || record.Response.Status == "in_progress") {
		if time.Since(time.Unix(record.Response.CreatedAt, 0)) > inferenceRequestTimeout {
			failed := record.Response
			failed.Status = "failed"
			failed.Error = map[string]string{
				"code":    "server_error",
				"message": "The generation was interrupted by a server restart and can never complete.",
			}
			inferenceResponseGlobals.Mu.Lock()
			inferenceResponseGlobals.Responses[record.Id] = inferenceStoredResponse{
				Response:     failed,
				Owner:        owner.Reference(),
				Conversation: record.Conversation,
				ContextAfter: record.ContextAfter,
				CreatedAt:    time.Now(),
			}
			inferenceResponseGlobals.Mu.Unlock()

			record.Response = failed
			_ = inferenceResponseStoreWrite(owner, username, record)
			return failed, true
		}
	}

	inferenceResponseGlobals.Mu.Lock()
	inferenceResponseGlobals.Responses[record.Id] = inferenceStoredResponse{
		Response:     record.Response,
		Owner:        owner.Reference(),
		Conversation: record.Conversation,
		ContextAfter: record.ContextAfter,
		CreatedAt:    time.Now(),
	}
	inferenceResponseGlobals.Mu.Unlock()
	return record.Response, true
}

func inferenceResponseStoreLookupRecord(owner apm.WalletOwner, username string, responseId string) (inferencePersistedResponse, bool) {
	inferenceResponseGlobals.Mu.RLock()
	stored, ok := inferenceResponseGlobals.Responses[responseId]
	inferenceResponseGlobals.Mu.RUnlock()

	if ok && stored.Owner == owner.Reference() && stored.Conversation != "" {
		if time.Since(stored.CreatedAt) > inferenceResponseStoreTTL {
			inferenceResponseStoreMemoryEvict(responseId)
		} else {
			return inferencePersistedResponse{
				Version:      1,
				Id:           stored.Response.Id,
				Owner:        stored.Owner,
				Conversation: stored.Conversation,
				ContextAfter: stored.ContextAfter,
				Response:     stored.Response,
			}, true
		}
	}

	return inferenceResponseStoreReadUnchecked(owner, username, responseId)
}

// Conversation serialization
// ---------------------------------------------------------------------------------------------------------------------
// inferenceResponseStoreInputItems converts plain text to a user message before storage. It preserves an input item
// array as separate items and preserves any other JSON value as one item. Empty and null input add no items.
//
// inferenceResponseConversationFromTurn appends the generated output items after the request input. Chain resolution
// can later combine these items with new input. New conversation values set both timestamps to the current time. The
// caller merges them with prior conversation data before persistence when a request extends an existing chain.

func inferenceResponseStoreInputItems(input json.RawMessage) []json.RawMessage {
	raw := input
	if len(raw) == 0 || string(raw) == "null" {
		return nil
	}

	var text string
	if err := json.Unmarshal(raw, &text); err == nil {
		item, _ := json.Marshal(map[string]any{
			"type": "message",
			"role": "user",
			"content": []map[string]any{{
				"type": "input_text",
				"text": text,
			}},
		})
		return []json.RawMessage{item}
	}

	var items []json.RawMessage
	if err := json.Unmarshal(raw, &items); err == nil {
		return items
	}

	return []json.RawMessage{raw}
}

func inferenceResponseConversationFromTurn(input json.RawMessage, response OaiResponse, conversationId string) *inferencePersistedConversation {
	now := time.Now().Unix()
	items := make([]json.RawMessage, 0, len(input)+len(response.Output))
	items = append(items, inferenceResponseStoreInputItems(input)...)
	for _, output := range response.Output {
		if encoded, err := json.Marshal(output); err == nil {
			items = append(items, encoded)
		}
	}

	return &inferencePersistedConversation{
		Version:   1,
		Id:        conversationId,
		CreatedAt: now,
		UpdatedAt: now,
		Items:     items,
	}
}

func conversationIdOf(conversation *inferencePersistedConversation) string {
	if conversation == nil {
		return ""
	}
	return conversation.Id
}

func contextAfterOf(conversation *inferencePersistedConversation) int {
	if conversation == nil {
		return 0
	}
	return len(conversation.Items)
}

// Persisted filesystem operations
// ---------------------------------------------------------------------------------------------------------------------
// These operations read, write, and delete versioned response and conversation files. Reads have a fixed size limit and
// reject invalid JSON, unknown versions, mismatched IDs, and records for another wallet owner. A failure to open or
// validate a file appears as missing state to callers.
//
// Writes set the owner from the current wallet, format the JSON, and replace files atomically. Response files contain
// the public response and its conversation position. Conversation files contain the ordered input and output items.
// Delete operations ignore missing files and log filesystem deletion failures.

func inferenceResponseStoreRead(owner apm.WalletOwner, username string, responseId string) (inferencePersistedResponse, bool) {
	return inferenceResponseStoreReadUnchecked(owner, username, responseId)
}

func inferenceResponseStoreReadUnchecked(owner apm.WalletOwner, username string, responseId string) (inferencePersistedResponse, bool) {
	if !inferenceResponseStoreValidId(responseId) {
		return inferencePersistedResponse{}, false
	}
	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return inferencePersistedResponse{}, false
	}

	path := filepath.Join(inferenceResponseStoreResponsesDir(basePath), responseId+".json")
	data, err := filesystem.ReadFile(path, inferenceResponseMaxJSONSize)
	if err != nil {
		return inferencePersistedResponse{}, false
	}

	var record inferencePersistedResponse
	if jsonErr := json.Unmarshal(data, &record); jsonErr != nil {
		return inferencePersistedResponse{}, false
	}
	if record.Version != 1 || record.Id != responseId || record.Owner != owner.Reference() {
		return inferencePersistedResponse{}, false
	}
	return record, true
}

func inferenceResponseStoreConversationRead(owner apm.WalletOwner, username string, conversationId string) (inferencePersistedConversation, bool) {
	if !inferenceResponseStoreValidId(conversationId) {
		return inferencePersistedConversation{}, false
	}
	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return inferencePersistedConversation{}, false
	}

	path := filepath.Join(inferenceResponseStoreConversationsDir(basePath), conversationId+".json")
	data, err := filesystem.ReadFile(path, inferenceResponseMaxJSONSize)
	if err != nil {
		return inferencePersistedConversation{}, false
	}

	var record inferencePersistedConversation
	if jsonErr := json.Unmarshal(data, &record); jsonErr != nil {
		return inferencePersistedConversation{}, false
	}
	if record.Version != 1 || record.Id != conversationId || record.Owner != owner.Reference() {
		return inferencePersistedConversation{}, false
	}
	return record, true
}

func inferenceResponseStoreWrite(owner apm.WalletOwner, username string, record inferencePersistedResponse) *util.HttpError {
	record.Owner = owner.Reference()
	if !inferenceResponseStoreValidId(record.Id) {
		return util.ServerHttpError("invalid inference response id")
	}

	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return err
	}

	data, jsonErr := json.MarshalIndent(record, "", "  ")
	if jsonErr != nil {
		return util.ServerHttpError("could not encode inference response state")
	}
	data = append(data, '\n')

	path := filepath.Join(inferenceResponseStoreResponsesDir(basePath), record.Id+".json")
	if writeErr := filesystem.WriteFileAtomic(path, data, 0660); writeErr != nil {
		return writeErr
	}
	return nil
}

func inferenceResponseStoreConversationWrite(owner apm.WalletOwner, username string, record inferencePersistedConversation) *util.HttpError {
	record.Owner = owner.Reference()
	if !inferenceResponseStoreValidId(record.Id) {
		return util.ServerHttpError("invalid inference conversation id")
	}

	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return err
	}

	data, jsonErr := json.MarshalIndent(record, "", "  ")
	if jsonErr != nil {
		return util.ServerHttpError("could not encode inference conversation state")
	}
	data = append(data, '\n')

	path := filepath.Join(inferenceResponseStoreConversationsDir(basePath), record.Id+".json")
	if writeErr := filesystem.WriteFileAtomic(path, data, 0660); writeErr != nil {
		return writeErr
	}
	return nil
}

func inferenceResponseStoreDelete(owner apm.WalletOwner, username string, responseId string) {
	if !inferenceResponseStoreValidId(responseId) {
		return
	}
	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return
	}

	path := filepath.Join(inferenceResponseStoreResponsesDir(basePath), responseId+".json")
	if info, statErr := filesystem.Stat(path); statErr == nil && !info.IsDir() {
		if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
			log.Warn("Could not delete inference response state %s: %s", path, deleteErr)
		}
	}
}

func inferenceResponseStoreConversationDelete(owner apm.WalletOwner, username string, conversationId string) {
	if !inferenceResponseStoreValidId(conversationId) {
		return
	}
	basePath, err := inferenceResponseStoreBasePath(owner, username)
	if err != nil {
		return
	}

	path := filepath.Join(inferenceResponseStoreConversationsDir(basePath), conversationId+".json")
	if info, statErr := filesystem.Stat(path); statErr == nil && !info.IsDir() {
		if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
			log.Warn("Could not delete inference conversation state %s: %s", path, deleteErr)
		}
	}
}

// Paths and identity
// ---------------------------------------------------------------------------------------------------------------------
// inferenceResponseStoreBasePath selects the member files location from the username and optional project ID. It rejects
// requests without a username and returns the normal payment error when the selected drive is locked.
//
// File paths use generated response and conversation IDs as names. inferenceResponseStoreValidId rejects empty IDs,
// path components, and parent traversal before any read, write, or delete operation constructs a path.

func inferenceResponseStoreBasePath(owner apm.WalletOwner, username string) (string, *util.HttpError) {
	if username == "" {
		return "", util.ServerHttpError("no username attached to inference token")
	}

	project := util.OptNone[string]()
	if owner.ProjectId != "" {
		project = util.OptValue(owner.ProjectId)
	}
	basePath, drive, err := filesystem.InitializeMemberFiles(username, project)
	if err != nil {
		return "", err
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return "", util.PaymentError()
	}
	return basePath, nil
}

func inferenceResponseStoreValidId(id string) bool {
	if id == "" || filepath.Base(id) != id || strings.Contains(id, "..") {
		return false
	}
	return true
}

func inferenceResponseStoreResponsesDir(basePath string) string {
	return filepath.Join(basePath, inferenceResponseResourcesSubPath, "Responses")
}

func inferenceResponseStoreConversationsDir(basePath string) string {
	return filepath.Join(basePath, inferenceResponseResourcesSubPath, "Conversations")
}

// Retention integration
// ---------------------------------------------------------------------------------------------------------------------
// InferenceDriveScanned is the retention entry point called after a periodic filesystem scan. It maps home drives to
// their user and project member files drives to their member user. Other drive types do not contain this state.
//
// inferenceResponseStoreSweep checks both response and conversation directories. It ignores hidden entries,
// non-JSON entries, directories, and files it cannot inspect. Files at least 30 days old become eligible for deletion.
// A deletion failure is logged, and a later drive scan can try again.

func InferenceDriveScanned(drive *orc.Drive, internalPath string) {
	descriptor, ok := filesystem.ParseDriveDescriptor(util.OptValue(drive.ProviderGeneratedId))
	if !ok {
		return
	}

	switch descriptor.Type {
	case filesystem.DriveDescriptorTypeHome:
		inferenceResponseStoreSweep(descriptor.PrimaryReference, internalPath)
	case filesystem.DriveDescriptorTypeMemberFiles:
		inferenceResponseStoreSweep(descriptor.SecondaryReference, internalPath)
	default:
	}
}

func inferenceResponseStoreSweep(username string, internalPath string) {
	if username == "" || strings.Contains(username, "/") || strings.Contains(username, "..") {
		return
	}

	cutoff := time.Now().Add(-inferenceResponseRetention)
	for _, dir := range []string{"Responses", "Conversations"} {
		entries, err := filesystem.ListDirNames(filepath.Join(internalPath, inferenceResponseResourcesSubPath, dir))
		if err != nil {
			continue
		}

		for _, entry := range entries {
			if !strings.HasSuffix(entry, ".json") || strings.HasPrefix(entry, ".") {
				continue
			}

			path := filepath.Join(internalPath, inferenceResponseResourcesSubPath, dir, entry)
			info, statErr := filesystem.Stat(path)
			if statErr != nil || info.IsDir() {
				continue
			}

			if info.ModTime().After(cutoff) {
				continue
			}

			if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
				log.Warn("Could not delete expired inference response state %s: %s", path, deleteErr)
			}
		}
	}
}

// Memory-cache primitives
// ---------------------------------------------------------------------------------------------------------------------
// inferenceResponseStoreMemoryEvict removes one response under the cache lock. inferenceResponseStoreCleanLocked removes
// all entries older than the 30-minute cache lifetime and requires its caller to hold the write lock. Cache removal does
// not delete persisted files. A later lookup can restore a stored response from its file.
//
// Explicit deletion evicts memory before it removes the response file. Expiry cleanup runs on the normal response write
// path. Persisted reads can add entries without a cleanup pass, but each restored entry still receives a new cache time.

func inferenceResponseStoreMemoryEvict(id string) {
	inferenceResponseGlobals.Mu.Lock()
	delete(inferenceResponseGlobals.Responses, id)
	inferenceResponseGlobals.Mu.Unlock()
}

func inferenceResponseStoreCleanLocked() {
	threshold := time.Now().Add(-inferenceResponseStoreTTL)
	for id, stored := range inferenceResponseGlobals.Responses {
		if stored.CreatedAt.Before(threshold) {
			delete(inferenceResponseGlobals.Responses, id)
		}
	}
}
