package inference

import (
	"encoding/json"
	"path/filepath"
	"strings"
	"time"

	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// Persisted Responses API state
// =====================================================================================================================
// This file implements the state store required for supporting the OAI Responses API. This keeps all of the server-side
// state in the user's home drive (see filesystem.InitializeMemberFiles). The file layout is:
//
//     <member-files>/Inference/Resources/
//         Conversations/<convId>.json
//         Responses/<respId>.json
//
// We store a single conversation file per conversation. We rewrite these on each turn, this is similar to the
// playground chat.
//
// Each response is stored in its own file. Overall this makes it easy enough to restore the in-memory cache from
// these files.
//
// The files are retained for 30 days, this roughly maps the guarantees that OAI provides. Hopefully this means that
// clients are already robust enough to be able to deal with this going away for threads that are older than 30 days.

const (
	inferenceResponseResourcesSubPath = "Inference/Resources"
	inferenceResponseRetention        = 30 * 24 * time.Hour
	inferenceResponseMaxJSONSize      = 8 * 1024 * 1024
)

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

// InferenceDriveScanned runs the retention sweep of the persisted state store. It is invoked by the filesystem scan
// machinery after a periodic scan of a drive completes. Failures are logged and retried on the next scan.
//
// State is stored on the wallet owner's drive: the home drive for user wallets and the member files drive of the
// creating user for project wallets. Both are swept.
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
		// Do nothing
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

func inferenceResponseStoreRead(owner apm.WalletOwner, username string, responseId string) (inferencePersistedResponse, bool) {
	record, ok := inferenceResponseStoreReadUnchecked(owner, username, responseId)
	if !ok {
		return inferencePersistedResponse{}, false
	}

	// A response record whose conversation file is missing is a torn write from the perspective of a reader.
	if record.Conversation != "" {
		_, ok := inferenceResponseStoreConversationRead(owner, username, record.Conversation)
		if !ok {
			return inferencePersistedResponse{}, false
		}
	}
	return record, true
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
