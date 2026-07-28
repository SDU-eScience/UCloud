package accounting

import (
	"cmp"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"slices"
	"strings"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type usageBreakdownScope struct {
	Resource           accapi.UsageBreakdownResource
	Usage              int64
	LastUpdatedAt      time.Time
	Workspace          accapi.WalletOwner
	IsExternallyFunded bool
}

type usageBreakdownCursor struct {
	SortBy        accapi.UsageBreakdownSortBy        `json:"sortBy"`
	SortDirection accapi.UsageBreakdownSortDirection `json:"sortDirection"`
	Usage         int64                              `json:"usage"`
	ReportedAt    int64                              `json:"reportedAt"`
	ResourceType  accapi.UsageBreakdownResourceType  `json:"resourceType"`
	ResourceId    string                             `json:"resourceId"`
}

var usageBreakdownRetrieveResources = coreutil.UsageBreakdownRetrieveResources

func UsageBreakdownBrowse(actor rpc.Actor, request accapi.UsageBreakdownBrowseRequest) (accapi.UsageBreakdownBrowseResponse, *util.HttpError) {
	reference := actor.Username
	if actor.Project.Present {
		if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			return accapi.UsageBreakdownBrowseResponse{}, util.HttpErr(http.StatusForbidden, "You need admin privileges in your project to view the usage breakdown")
		}
		reference = string(actor.Project.Value)
	}

	category := accapi.ProductCategoryIdV2{Name: request.CategoryName, Provider: request.CategoryProvider}
	scopes := internalUsageBreakdown(reference, category)
	resources := make([]coreutil.UsageBreakdownResource, 0, len(scopes))
	for _, scope := range scopes {
		resource := coreutil.UsageBreakdownResource{Type: string(scope.Resource.Type), Id: scope.Resource.Id}
		if scope.Workspace.Type == accapi.WalletOwnerTypeProject {
			resource.ProjectId = scope.Workspace.ProjectId
		} else {
			resource.CreatedBy = scope.Workspace.Username
		}
		resources = append(resources, resource)
	}
	metadata := usageBreakdownRetrieveResources(resources)
	items := usageBreakdownEnrich(scopes, metadata)
	return usageBreakdownPage(items, request), nil
}

func internalUsageBreakdown(reference string, category accapi.ProductCategoryIdV2) []usageBreakdownScope {
	accGlobals.Mu.RLock()
	defer accGlobals.Mu.RUnlock()

	owner := accGlobals.OwnersByReference[reference]
	bucket := accGlobals.BucketsByCategory[category]
	if owner == nil || bucket == nil {
		return []usageBreakdownScope{}
	}

	bucket.Mu.Lock()
	defer bucket.Mu.Unlock()
	wallet := bucket.WalletsByOwner[owner.Id]
	if wallet == nil {
		return []usageBreakdownScope{}
	}

	pending := []AccWalletId{wallet.Id}
	selectedWallets := map[AccWalletId]bool{}
	for len(pending) > 0 {
		walletId := pending[0]
		pending = pending[1:]
		if selectedWallets[walletId] {
			continue
		}
		selectedWallets[walletId] = true
		current := bucket.WalletsById[walletId]
		if current == nil {
			continue
		}
		for childId := range current.ChildrenUsage {
			child := bucket.WalletsById[childId]
			if child == nil {
				continue
			}
			group := child.AllocationsByParent[current.Id]
			if group != nil && lInternalGroupHasCommittedAllocation(bucket, group) {
				pending = append(pending, childId)
			}
		}
	}

	walletIds := make([]AccWalletId, 0, len(selectedWallets))
	for walletId := range selectedWallets {
		walletIds = append(walletIds, walletId)
	}

	externallyFunded := map[AccWalletId]bool{}
	for walletId := range selectedWallets {
		if walletId == wallet.Id {
			continue
		}
		current := bucket.WalletsById[walletId]
		if current == nil {
			continue
		}
		for parentId, group := range current.AllocationsByParent {
			if !selectedWallets[parentId] && lInternalGroupHasCommittedAllocation(bucket, group) {
				externallyFunded[walletId] = true
				break
			}
		}
	}

	result := make([]usageBreakdownScope, 0)
	for walletId := range selectedWallets {
		current := bucket.WalletsById[walletId]
		if current == nil {
			continue
		}
		for key, scope := range current.ScopedUsage {
			resource, ok := usageBreakdownParseResource(key, category.Provider)
			if !ok || scope.Usage == 0 {
				continue
			}
			result = append(result, usageBreakdownScope{
				Resource: resource, Usage: scope.Usage, LastUpdatedAt: scope.LastUpdatedAt,
				Workspace:          accGlobals.OwnersById[current.OwnedBy].WalletOwner(),
				IsExternallyFunded: externallyFunded[walletId],
			})
		}
	}
	return result
}

func usageBreakdownParseResource(key string, provider string) (accapi.UsageBreakdownResource, bool) {
	if id, ok := strings.CutPrefix(key, "drive-"); ok && id != "" {
		id = strings.TrimPrefix(id, provider+"-")
		return accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeDrive, Id: id}, true
	}
	if id, ok := strings.CutPrefix(key, "job-"); ok && id != "" {
		return accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: id}, true
	}
	return accapi.UsageBreakdownResource{}, false
}

func usageBreakdownResourceKey(resourceType string, id string) string {
	return resourceType + "\x00" + id
}

func usageBreakdownEnrich(scopes []usageBreakdownScope, metadata []coreutil.UsageBreakdownResource) []accapi.UsageBreakdownItem {
	metadataByResource := make(map[string]coreutil.UsageBreakdownResource, len(metadata))
	for _, resource := range metadata {
		metadataByResource[usageBreakdownResourceKey(resource.Type, resource.Id)] = resource
	}

	itemsByResource := make(map[string]accapi.UsageBreakdownItem, len(scopes))
	for _, scope := range scopes {
		key := usageBreakdownResourceKey(string(scope.Resource.Type), scope.Resource.Id)
		resource := metadataByResource[key]
		workspace := scope.Workspace
		workspaceTitle := ""
		if resource.ProjectId != "" {
			workspace = accapi.WalletOwnerProject(resource.ProjectId)
			workspaceTitle = resource.ProjectTitle
		} else if resource.CreatedBy != "" {
			workspace = accapi.WalletOwnerUser(resource.CreatedBy)
			workspaceTitle = resource.CreatedBy + "'s workspace"
		} else if workspace.Type == accapi.WalletOwnerTypeUser {
			workspaceTitle = workspace.Username + "'s workspace"
		}
		item, duplicate := itemsByResource[key]
		if duplicate {
			item.Usage += scope.Usage
			if scope.LastUpdatedAt.After(item.LastUpdatedAt.Time()) {
				item.LastUpdatedAt = fndapi.Timestamp(scope.LastUpdatedAt)
			}
			item.IsExternallyFunded = item.IsExternallyFunded || scope.IsExternallyFunded
			itemsByResource[key] = item
			continue
		}
		itemsByResource[key] = accapi.UsageBreakdownItem{
			Resource: scope.Resource, Usage: scope.Usage, LastUpdatedAt: fndapi.Timestamp(scope.LastUpdatedAt),
			Title: resource.Title, CreatedBy: resource.CreatedBy, Workspace: workspace,
			WorkspaceTitle: workspaceTitle, IsExternallyFunded: scope.IsExternallyFunded,
		}
	}
	items := make([]accapi.UsageBreakdownItem, 0, len(itemsByResource))
	for _, item := range itemsByResource {
		items = append(items, item)
	}
	return items
}

func usageBreakdownSort(request accapi.UsageBreakdownBrowseRequest) (accapi.UsageBreakdownSortBy, accapi.UsageBreakdownSortDirection) {
	sortBy := request.SortBy.GetOrDefault(accapi.UsageBreakdownSortByReportedAt)
	if sortBy != accapi.UsageBreakdownSortByUsage && sortBy != accapi.UsageBreakdownSortByReportedAt {
		sortBy = accapi.UsageBreakdownSortByReportedAt
	}
	direction := request.SortDirection.GetOrDefault(accapi.UsageBreakdownSortDescending)
	if direction != accapi.UsageBreakdownSortAscending && direction != accapi.UsageBreakdownSortDescending {
		direction = accapi.UsageBreakdownSortDescending
	}
	return sortBy, direction
}

func usageBreakdownCompare(item accapi.UsageBreakdownItem, cursor usageBreakdownCursor) int {
	result := 0
	if cursor.SortBy == accapi.UsageBreakdownSortByUsage {
		result = cmp.Compare(item.Usage, cursor.Usage)
	} else {
		result = cmp.Compare(item.LastUpdatedAt.UnixMilli(), cursor.ReportedAt)
	}
	if cursor.SortDirection == accapi.UsageBreakdownSortDescending {
		result = -result
	}
	if result != 0 {
		return result
	}
	if result = cmp.Compare(string(item.Resource.Type), string(cursor.ResourceType)); result != 0 {
		return result
	}
	return strings.Compare(item.Resource.Id, cursor.ResourceId)
}

func usageBreakdownCursorFor(item accapi.UsageBreakdownItem, sortBy accapi.UsageBreakdownSortBy, direction accapi.UsageBreakdownSortDirection) string {
	cursor, _ := json.Marshal(usageBreakdownCursor{
		SortBy: sortBy, SortDirection: direction, Usage: item.Usage, ReportedAt: item.LastUpdatedAt.UnixMilli(),
		ResourceType: item.Resource.Type, ResourceId: item.Resource.Id,
	})
	return base64.RawURLEncoding.EncodeToString(cursor)
}

func usageBreakdownPage(items []accapi.UsageBreakdownItem, request accapi.UsageBreakdownBrowseRequest) accapi.UsageBreakdownBrowseResponse {
	workspaceAutocomplete, createdByAutocomplete := usageBreakdownAutocomplete(items, request)
	filtered := make([]accapi.UsageBreakdownItem, 0, len(items))
	var totalUsage int64
	for _, item := range items {
		if request.FilterProject.Present && item.Workspace.ProjectId != request.FilterProject.Value {
			continue
		}
		if request.FilterCreatedBy.Present && item.CreatedBy != request.FilterCreatedBy.Value {
			continue
		}
		if request.FilterReportedAtMin.Present && item.LastUpdatedAt.Time().Before(fndapi.TimeFromUnixMilli(request.FilterReportedAtMin.Value).Time()) {
			continue
		}
		if request.FilterReportedAtMax.Present && item.LastUpdatedAt.Time().After(fndapi.TimeFromUnixMilli(request.FilterReportedAtMax.Value).Time()) {
			continue
		}
		if request.FilterUsageMin.Present && item.Usage < request.FilterUsageMin.Value {
			continue
		}
		if request.FilterUsageMax.Present && item.Usage > request.FilterUsageMax.Value {
			continue
		}
		filtered = append(filtered, item)
		totalUsage += item.Usage
	}

	sortBy, direction := usageBreakdownSort(request)
	slices.SortStableFunc(filtered, func(a, b accapi.UsageBreakdownItem) int {
		return usageBreakdownCompare(a, usageBreakdownCursorForItem(b, sortBy, direction))
	})
	start := 0
	if request.Next.Present {
		if raw, err := base64.RawURLEncoding.DecodeString(request.Next.Value); err == nil {
			var cursor usageBreakdownCursor
			if json.Unmarshal(raw, &cursor) == nil && cursor.SortBy == sortBy && cursor.SortDirection == direction {
				for start < len(filtered) && usageBreakdownCompare(filtered[start], cursor) <= 0 {
					start++
				}
			}
		}
	}
	pageSize := fndapi.ItemsPerPage(request.ItemsPerPage)
	end := min(start+pageSize, len(filtered))
	response := accapi.UsageBreakdownBrowseResponse{
		Items: filtered[start:end], ItemsPerPage: pageSize, TotalUsage: totalUsage, TotalCount: len(filtered),
		WorkspaceAutocomplete: workspaceAutocomplete, CreatedByAutocomplete: createdByAutocomplete,
	}
	if end < len(filtered) {
		response.Next.Set(usageBreakdownCursorFor(filtered[end-1], sortBy, direction))
	}
	return response
}

func usageBreakdownAutocomplete(items []accapi.UsageBreakdownItem, request accapi.UsageBreakdownBrowseRequest) ([]accapi.UsageBreakdownAutocompleteSuggestion, []accapi.UsageBreakdownAutocompleteSuggestion) {
	workspaces := map[string]accapi.UsageBreakdownAutocompleteSuggestion{}
	createdBy := map[string]accapi.UsageBreakdownAutocompleteSuggestion{}
	workspaceQuery := strings.ToLower(strings.TrimSpace(request.WorkspaceSearch.Value))
	createdByQuery := strings.ToLower(strings.TrimSpace(request.CreatedBySearch.Value))

	for _, item := range items {
		if request.FilterReportedAtMin.Present && item.LastUpdatedAt.Time().Before(fndapi.TimeFromUnixMilli(request.FilterReportedAtMin.Value).Time()) {
			continue
		}
		if request.FilterReportedAtMax.Present && item.LastUpdatedAt.Time().After(fndapi.TimeFromUnixMilli(request.FilterReportedAtMax.Value).Time()) {
			continue
		}
		if request.FilterUsageMin.Present && item.Usage < request.FilterUsageMin.Value {
			continue
		}
		if request.FilterUsageMax.Present && item.Usage > request.FilterUsageMax.Value {
			continue
		}

		if request.WorkspaceSearch.Present {
			value := item.Workspace.Username
			suggestionType := accapi.UsageBreakdownAutocompleteCreatedBy
			if item.Workspace.Type == accapi.WalletOwnerTypeProject {
				value = item.Workspace.ProjectId
				suggestionType = accapi.UsageBreakdownAutocompleteProject
			}
			label := item.WorkspaceTitle
			if label == "" {
				label = value
			}
			if value != "" && (workspaceQuery == "" || strings.Contains(strings.ToLower(label), workspaceQuery) || strings.Contains(strings.ToLower(value), workspaceQuery)) {
				key := string(suggestionType) + "\x00" + value
				workspaces[key] = accapi.UsageBreakdownAutocompleteSuggestion{Type: suggestionType, Value: value, Label: label}
			}
		}

		if request.CreatedBySearch.Present && item.Resource.Type == accapi.UsageBreakdownResourceTypeJob && item.CreatedBy != "" &&
			(createdByQuery == "" || strings.Contains(strings.ToLower(item.CreatedBy), createdByQuery)) {
			createdBy[item.CreatedBy] = accapi.UsageBreakdownAutocompleteSuggestion{
				Type: accapi.UsageBreakdownAutocompleteCreatedBy, Value: item.CreatedBy, Label: item.CreatedBy,
			}
		}
	}

	return usageBreakdownSortedSuggestions(workspaces, workspaceQuery), usageBreakdownSortedSuggestions(createdBy, createdByQuery)
}

func usageBreakdownSortedSuggestions(source map[string]accapi.UsageBreakdownAutocompleteSuggestion, query string) []accapi.UsageBreakdownAutocompleteSuggestion {
	result := make([]accapi.UsageBreakdownAutocompleteSuggestion, 0, len(source))
	for _, suggestion := range source {
		result = append(result, suggestion)
	}
	slices.SortFunc(result, func(a, b accapi.UsageBreakdownAutocompleteSuggestion) int {
		aPrefix := strings.HasPrefix(strings.ToLower(a.Label), query) || strings.HasPrefix(strings.ToLower(a.Value), query)
		bPrefix := strings.HasPrefix(strings.ToLower(b.Label), query) || strings.HasPrefix(strings.ToLower(b.Value), query)
		if aPrefix != bPrefix {
			if aPrefix {
				return -1
			}
			return 1
		}
		if result := strings.Compare(strings.ToLower(a.Label), strings.ToLower(b.Label)); result != 0 {
			return result
		}
		if result := strings.Compare(a.Value, b.Value); result != 0 {
			return result
		}
		return strings.Compare(string(a.Type), string(b.Type))
	})
	return result[:min(10, len(result))]
}

func usageBreakdownCursorForItem(item accapi.UsageBreakdownItem, sortBy accapi.UsageBreakdownSortBy, direction accapi.UsageBreakdownSortDirection) usageBreakdownCursor {
	return usageBreakdownCursor{
		SortBy: sortBy, SortDirection: direction, Usage: item.Usage, ReportedAt: item.LastUpdatedAt.UnixMilli(),
		ResourceType: item.Resource.Type, ResourceId: item.Resource.Id,
	}
}
