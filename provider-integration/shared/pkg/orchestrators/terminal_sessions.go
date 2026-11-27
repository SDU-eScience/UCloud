package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const terminalSessionsNamespace = "jobs/terminalSessions"
const terminalSessionsProviderNamespace = "jobs.provider.terminalSessions"

type TerminalSessionSplitDirection string

const (
	TerminalSessionSplitHorizontal TerminalSessionSplitDirection = "horizontal"
	TerminalSessionSplitVertical   TerminalSessionSplitDirection = "vertical"
)

type TerminalSessionLayoutNode struct {
	Kind      string                        `json:"kind"`
	PaneId    string                        `json:"paneId,omitempty"`
	Direction TerminalSessionSplitDirection `json:"direction,omitempty"`
	Ratio     float64                       `json:"ratio,omitempty"`
	First     *TerminalSessionLayoutNode    `json:"first,omitempty"`
	Second    *TerminalSessionLayoutNode    `json:"second,omitempty"`
}

type TerminalSessionPane struct {
	Id            string        `json:"id"`
	TabId         string        `json:"tabId"`
	Rank          int           `json:"rank"`
	Title         string        `json:"title"`
	TitlePinned   bool          `json:"titlePinned"`
	BackendHandle string        `json:"backendHandle"`
	CreatedAt     fnd.Timestamp `json:"createdAt"`
}

type TerminalSessionTab struct {
	Id           string                    `json:"id"`
	Title        string                    `json:"title"`
	TitlePinned  bool                      `json:"titlePinned"`
	Position     int                       `json:"position"`
	ActivePaneId string                    `json:"activePaneId,omitempty"`
	Layout       TerminalSessionLayoutNode `json:"layout"`
	Panes        []TerminalSessionPane     `json:"panes"`
}

type TerminalSessionSummary struct {
	Id               string        `json:"id"`
	JobId            string        `json:"jobId"`
	JobTitle         string        `json:"jobTitle"`
	ApplicationName  string        `json:"applicationName"`
	ApplicationTitle string        `json:"applicationTitle"`
	ProviderId       string        `json:"providerId"`
	Replicas         int           `json:"replicas"`
	Title            string        `json:"title"`
	TabCount         int           `json:"tabCount"`
	PaneCount        int           `json:"paneCount"`
	Revision         int64         `json:"revision"`
	CreatedAt        fnd.Timestamp `json:"createdAt"`
	UpdatedAt        fnd.Timestamp `json:"updatedAt"`
	LastAccessedAt   fnd.Timestamp `json:"lastAccessedAt"`
}

type TerminalSessionDetail struct {
	Id               string               `json:"id"`
	JobId            string               `json:"jobId"`
	JobTitle         string               `json:"jobTitle"`
	ApplicationName  string               `json:"applicationName"`
	ApplicationTitle string               `json:"applicationTitle"`
	ProviderId       string               `json:"providerId"`
	Replicas         int                  `json:"replicas"`
	Title            string               `json:"title"`
	ActiveTabId      string               `json:"activeTabId,omitempty"`
	Revision         int64                `json:"revision"`
	CreatedAt        fnd.Timestamp        `json:"createdAt"`
	UpdatedAt        fnd.Timestamp        `json:"updatedAt"`
	LastAccessedAt   fnd.Timestamp        `json:"lastAccessedAt"`
	Tabs             []TerminalSessionTab `json:"tabs"`
}

type TerminalSessionsBrowseRequest struct {
	JobId util.Option[string] `json:"jobId"`
}

type TerminalSessionsBrowseResponse struct {
	Sessions []TerminalSessionSummary `json:"sessions"`
}

type TerminalSessionRetrieveRequest struct {
	SessionId string `json:"sessionId"`
}

type TerminalSessionOpenForJobRequest struct {
	JobId string `json:"jobId"`
	Rank  int    `json:"rank"`
}

type TerminalSessionCreateRequest struct {
	JobId string `json:"jobId"`
	Title string `json:"title,omitempty"`
	Rank  int    `json:"rank"`
}

type TerminalSessionTabUpdate struct {
	Id           string                     `json:"id"`
	Title        util.Option[string]        `json:"title"`
	TitlePinned  util.Option[bool]          `json:"titlePinned"`
	Position     util.Option[int]           `json:"position"`
	ActivePaneId util.Option[string]        `json:"activePaneId"`
	Layout       *TerminalSessionLayoutNode `json:"layout,omitempty"`
}

type TerminalSessionPaneUpdate struct {
	Id          string              `json:"id"`
	Title       util.Option[string] `json:"title"`
	TitlePinned util.Option[bool]   `json:"titlePinned"`
}

type TerminalSessionUpdateRequest struct {
	SessionId   string                      `json:"sessionId"`
	Revision    int64                       `json:"revision"`
	Title       util.Option[string]         `json:"title"`
	ActiveTabId util.Option[string]         `json:"activeTabId"`
	Tabs        []TerminalSessionTabUpdate  `json:"tabs,omitempty"`
	Panes       []TerminalSessionPaneUpdate `json:"panes,omitempty"`
}

type TerminalSessionCreateTabRequest struct {
	SessionId string `json:"sessionId"`
	Title     string `json:"title,omitempty"`
	Rank      int    `json:"rank"`
}

type TerminalSessionSplitPaneRequest struct {
	SessionId    string                        `json:"sessionId"`
	TargetPaneId string                        `json:"targetPaneId"`
	Direction    TerminalSessionSplitDirection `json:"direction"`
	PlaceFirst   bool                          `json:"placeFirst"`
	Rank         util.Option[int]              `json:"rank"`
}

type TerminalSessionClosePaneRequest struct {
	SessionId string `json:"sessionId"`
	PaneId    string `json:"paneId"`
}

type TerminalSessionCloseTabRequest struct {
	SessionId string `json:"sessionId"`
	TabId     string `json:"tabId"`
}

type TerminalSessionCloseResponse struct {
	SessionDeleted bool                   `json:"sessionDeleted"`
	Session        *TerminalSessionDetail `json:"session,omitempty"`
}

type TerminalSessionShutdownRequest struct {
	SessionId string `json:"sessionId"`
}

type TerminalSessionConnectRequest struct {
	SessionId string `json:"sessionId"`
}

type TerminalSessionConnectResponse struct {
	ProviderDomain string `json:"providerDomain"`
	ProviderId     string `json:"providerId"`
	Token          string `json:"token"`
	WsPath         string `json:"wsPath"`
}

type TerminalSessionProviderConnectRequest struct {
	SessionId string `json:"sessionId"`
	Job       Job    `json:"job"`
	Username  string `json:"username"`
}

type TerminalSessionProviderConnectResponse struct {
	Token          string `json:"token"`
	DomainOverride string `json:"domainOverride,omitempty"`
}

type TerminalSessionProviderHandleTarget struct {
	Rank          int    `json:"rank"`
	BackendHandle string `json:"backendHandle"`
}

type TerminalSessionProviderKillHandlesRequest struct {
	Job     Job                                   `json:"job"`
	Handles []TerminalSessionProviderHandleTarget `json:"handles"`
}

var JobsTerminalSessionsBrowse = rpc.Call[TerminalSessionsBrowseRequest, TerminalSessionsBrowseResponse]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var JobsTerminalSessionRetrieve = rpc.Call[TerminalSessionRetrieveRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var JobsTerminalSessionOpenForJob = rpc.Call[TerminalSessionOpenForJobRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "openForJob",
}

var JobsTerminalSessionCreate = rpc.Call[TerminalSessionCreateRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var JobsTerminalSessionUpdate = rpc.Call[TerminalSessionUpdateRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "update",
}

var JobsTerminalSessionCreateTab = rpc.Call[TerminalSessionCreateTabRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "createTab",
}

var JobsTerminalSessionSplitPane = rpc.Call[TerminalSessionSplitPaneRequest, TerminalSessionDetail]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "splitPane",
}

var JobsTerminalSessionClosePane = rpc.Call[TerminalSessionClosePaneRequest, TerminalSessionCloseResponse]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "closePane",
}

var JobsTerminalSessionCloseTab = rpc.Call[TerminalSessionCloseTabRequest, TerminalSessionCloseResponse]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "closeTab",
}

var JobsTerminalSessionShutdown = rpc.Call[TerminalSessionShutdownRequest, util.Empty]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "shutdown",
}

var JobsTerminalSessionConnect = rpc.Call[TerminalSessionConnectRequest, TerminalSessionConnectResponse]{
	BaseContext: terminalSessionsNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "connect",
}

var JobsProviderTerminalSessionConnect = rpc.Call[TerminalSessionProviderConnectRequest, TerminalSessionProviderConnectResponse]{
	BaseContext: terminalSessionsProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "connect",
}

var JobsProviderTerminalKillHandles = rpc.Call[TerminalSessionProviderKillHandlesRequest, util.Empty]{
	BaseContext: terminalSessionsProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "killHandles",
}
