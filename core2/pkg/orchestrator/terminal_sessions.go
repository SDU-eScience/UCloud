package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type terminalSessionRow struct {
	Id             string
	JobId          int64
	ProviderId     string
	CreatedBy      string
	Title          string
	ActiveTabId    sql.NullString
	Revision       int64
	CreatedAt      time.Time
	UpdatedAt      time.Time
	LastAccessedAt time.Time
}

type terminalTabRow struct {
	Id           string
	SessionId    string
	Title        string
	TitlePinned  bool
	Position     int
	ActivePaneId sql.NullString
	Layout       []byte
}

type terminalPaneRow struct {
	Id            string
	SessionId     string
	TabId         string
	Rank          int
	Title         string
	TitlePinned   bool
	BackendHandle string
	CreatedAt     time.Time
}

func initTerminalSessions() {
	orcapi.JobsTerminalSessionsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionsBrowseRequest) (orcapi.TerminalSessionsBrowseResponse, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionsBrowseResponse{}, featureErr
		}

		var filterJobId sql.NullInt64
		if request.JobId.Present && strings.TrimSpace(request.JobId.Value) != "" {
			parsed := ResourceParseId(request.JobId.Value)
			if parsed <= 0 {
				return orcapi.TerminalSessionsBrowseResponse{}, util.HttpErr(http.StatusBadRequest, "invalid job id")
			}
			filterJobId = sql.NullInt64{
				Int64: int64(parsed),
				Valid: true,
			}
		}

		type browseRow struct {
			Id               string
			JobId            int64
			ProviderId       string
			Title            string
			Revision         int64
			CreatedAt        time.Time
			UpdatedAt        time.Time
			LastAccessedAt   time.Time
			JobName          sql.NullString
			ApplicationName  string
			Replicas         int
			JobState         string
			TabCount         int
			PaneCount        int
		}

		rows := db.NewTx(func(tx *db.Transaction) []browseRow {
			return db.Select[browseRow](
				tx,
				`
					select
						s.id,
						s.job_id,
						s.provider_id,
						s.title,
						s.revision,
						s.created_at,
						s.updated_at,
						s.last_accessed_at,
						j.name as job_name,
						j.application_name,
						j.replicas,
						j.current_state as job_state,
						(
							select count(*)
							from terminal.tabs t
							where t.session_id = s.id
						) as tab_count,
						(
							select count(*)
							from terminal.panes p
							where p.session_id = s.id
						) as pane_count
					from
						terminal.sessions s
						join app_orchestrator.jobs j on j.resource = s.job_id
					where
						s.created_by = :username
						and (cast(:job_id as bigint) is null or s.job_id = cast(:job_id as bigint))
					order by s.last_accessed_at desc, s.created_at desc
				`,
				db.Params{
					"username": info.Actor.Username,
					"job_id":   filterJobId,
				},
			)
		})

		staleSessionIds := make([]string, 0)
		summaries := make([]orcapi.TerminalSessionSummary, 0, len(rows))
		for _, row := range rows {
			jobState := orcapi.JobState(row.JobState)
			if jobState.IsFinal() {
				staleSessionIds = append(staleSessionIds, row.Id)
				continue
			}

			jobIdStr := strconv.FormatInt(row.JobId, 10)
			jobTitle := row.JobName.String
			if strings.TrimSpace(jobTitle) == "" {
				jobTitle = fmt.Sprintf("Job %s", jobIdStr)
			}

			summaries = append(summaries, orcapi.TerminalSessionSummary{
				Id:               row.Id,
				JobId:            jobIdStr,
				JobTitle:         jobTitle,
				ApplicationName:  row.ApplicationName,
				ApplicationTitle: row.ApplicationName,
				ProviderId:       row.ProviderId,
				Replicas:         row.Replicas,
				Title:            row.Title,
				TabCount:         row.TabCount,
				PaneCount:        row.PaneCount,
				Revision:         row.Revision,
				CreatedAt:        fndapi.Timestamp(row.CreatedAt),
				UpdatedAt:        fndapi.Timestamp(row.UpdatedAt),
				LastAccessedAt:   fndapi.Timestamp(row.LastAccessedAt),
			})
		}

		if len(staleSessionIds) > 0 {
			db.NewTx0(func(tx *db.Transaction) {
				for _, staleId := range staleSessionIds {
					db.Exec(
						tx,
						`
							delete from terminal.sessions
							where id = :id
						`,
						db.Params{
							"id": staleId,
						},
					)
				}
			})
		}

		return orcapi.TerminalSessionsBrowseResponse{
			Sessions: summaries,
		}, nil
	})

	orcapi.JobsTerminalSessionRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionRetrieveRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		detail := terminalSessionBuildDetail(sessionRow, job)
		return detail, nil
	})

	orcapi.JobsTerminalSessionOpenForJob.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionOpenForJobRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		job, jobResourceId, err := terminalSessionValidateJob(info.Actor, request.JobId, request.Rank)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		var existing terminalSessionRow
		var found bool
		db.NewTx0(func(tx *db.Transaction) {
			existing, found = db.Get[terminalSessionRow](
				tx,
				`
					select
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
					from terminal.sessions
					where
						created_by = :username
						and job_id = :job_id
					order by last_accessed_at desc, created_at desc
					limit 1
				`,
				db.Params{
					"username": info.Actor.Username,
					"job_id":   int64(jobResourceId),
				},
			)
		})

		if found {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						update terminal.sessions
						set last_accessed_at = now()
						where id = :id
					`,
					db.Params{
						"id": existing.Id,
					},
				)
			})
			existing.LastAccessedAt = time.Now()
			return terminalSessionBuildDetail(existing, job), nil
		}

		created := terminalSessionCreateInDb(info.Actor.Username, job, jobResourceId, "", request.Rank)
		return terminalSessionBuildDetail(created, job), nil
	})

	orcapi.JobsTerminalSessionCreate.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionCreateRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		job, jobResourceId, err := terminalSessionValidateJob(info.Actor, request.JobId, request.Rank)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		created := terminalSessionCreateInDb(info.Actor.Username, job, jobResourceId, request.Title, request.Rank)
		return terminalSessionBuildDetail(created, job), nil
	})

	orcapi.JobsTerminalSessionUpdate.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionUpdateRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		if request.Revision > 0 && request.Revision != sessionRow.Revision {
			return orcapi.TerminalSessionDetail{}, util.HttpErr(http.StatusConflict, "terminal session has been modified")
		}

		for _, tabUpdate := range request.Tabs {
			if tabUpdate.Layout != nil {
				terminalSessionNormalizeLayout(tabUpdate.Layout)
			}
		}

		updatedRow := db.NewTx(func(tx *db.Transaction) terminalSessionRow {
			nextTitle := sessionRow.Title
			if request.Title.Present && strings.TrimSpace(request.Title.Value) != "" {
				nextTitle = strings.TrimSpace(request.Title.Value)
			}

			nextActiveTabId := sessionRow.ActiveTabId
			if request.ActiveTabId.Present {
				nextActiveTabId = sql.NullString{
					String: request.ActiveTabId.Value,
					Valid:  request.ActiveTabId.Value != "",
				}
			}

			for _, tabUpdate := range request.Tabs {
				existingTab, ok := db.Get[terminalTabRow](
					tx,
					`
						select
							id,
							session_id,
							title,
							title_pinned,
							position,
							active_pane_id,
							layout
						from terminal.tabs
						where
							id = :id
							and session_id = :session_id
					`,
					db.Params{
						"id":         tabUpdate.Id,
						"session_id": sessionRow.Id,
					},
				)
				if !ok {
					continue
				}

				tabTitle := existingTab.Title
				if tabUpdate.Title.Present && strings.TrimSpace(tabUpdate.Title.Value) != "" {
					tabTitle = strings.TrimSpace(tabUpdate.Title.Value)
				}
				tabPinned := existingTab.TitlePinned
				if tabUpdate.TitlePinned.Present {
					tabPinned = tabUpdate.TitlePinned.Value
				}
				tabPos := existingTab.Position
				if tabUpdate.Position.Present {
					tabPos = tabUpdate.Position.Value
				}
				tabActivePane := existingTab.ActivePaneId
				if tabUpdate.ActivePaneId.Present {
					tabActivePane = sql.NullString{
						String: tabUpdate.ActivePaneId.Value,
						Valid:  tabUpdate.ActivePaneId.Value != "",
					}
				}
				tabLayout := existingTab.Layout
				if tabUpdate.Layout != nil {
					encoded, marshalErr := json.Marshal(tabUpdate.Layout)
					if marshalErr == nil {
						tabLayout = encoded
					}
				}

				db.Exec(
					tx,
					`
						update terminal.tabs
						set
							title = :title,
							title_pinned = :title_pinned,
							position = :position,
							active_pane_id = :active_pane_id,
							layout = :layout
						where
							id = :id
							and session_id = :session_id
					`,
					db.Params{
						"id":             tabUpdate.Id,
						"session_id":     sessionRow.Id,
						"title":          tabTitle,
						"title_pinned":   tabPinned,
						"position":       tabPos,
						"active_pane_id": tabActivePane,
						"layout":         tabLayout,
					},
				)
			}

			for _, paneUpdate := range request.Panes {
				existingPane, ok := db.Get[terminalPaneRow](
					tx,
					`
						select
							id,
							session_id,
							tab_id,
							rank,
							title,
							title_pinned,
							backend_handle,
							created_at
						from terminal.panes
						where
							id = :id
							and session_id = :session_id
					`,
					db.Params{
						"id":         paneUpdate.Id,
						"session_id": sessionRow.Id,
					},
				)
				if !ok {
					continue
				}

				paneTitle := existingPane.Title
				if paneUpdate.Title.Present && strings.TrimSpace(paneUpdate.Title.Value) != "" {
					paneTitle = strings.TrimSpace(paneUpdate.Title.Value)
				}
				panePinned := existingPane.TitlePinned
				if paneUpdate.TitlePinned.Present {
					panePinned = paneUpdate.TitlePinned.Value
				}

				db.Exec(
					tx,
					`
						update terminal.panes
						set
							title = :title,
							title_pinned = :title_pinned
						where
							id = :id
							and session_id = :session_id
					`,
					db.Params{
						"id":           paneUpdate.Id,
						"session_id":   sessionRow.Id,
						"title":        paneTitle,
						"title_pinned": panePinned,
					},
				)
			}

			row, _ := db.Get[terminalSessionRow](
				tx,
				`
					update terminal.sessions
					set
						title = :title,
						active_tab_id = :active_tab_id,
						revision = revision + 1,
						updated_at = now(),
						last_accessed_at = now()
					where id = :id
					returning
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
				`,
				db.Params{
					"id":            sessionRow.Id,
					"title":         nextTitle,
					"active_tab_id": nextActiveTabId,
				},
			)
			return row
		})

		return terminalSessionBuildDetail(updatedRow, job), nil
	})

	orcapi.JobsTerminalSessionCreateTab.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionCreateTabRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		rank := terminalSessionClampRank(request.Rank, job.Specification.Replicas)

		updatedRow := db.NewTx(func(tx *db.Transaction) terminalSessionRow {
			maxPosRow, ok := db.Get[struct{ MaxPos sql.NullInt64 }](
				tx,
				`
					select max(position) as max_pos
					from terminal.tabs
					where session_id = :session_id
				`,
				db.Params{
					"session_id": sessionRow.Id,
				},
			)
			nextPos := 0
			if ok && maxPosRow.MaxPos.Valid {
				nextPos = int(maxPosRow.MaxPos.Int64) + 1
			}

			tabId := terminalSessionNewId("tab")
			paneId := terminalSessionNewId("pane")
			handle := terminalSessionNewHandle()
			tabTitle := strings.TrimSpace(request.Title)
			tabPinned := tabTitle != ""
			if tabTitle == "" {
				tabTitle = fmt.Sprintf("Tab %d", nextPos+1)
			}

			layoutBytes, _ := json.Marshal(orcapi.TerminalSessionLayoutNode{
				Kind:   "pane",
				PaneId: paneId,
			})

			db.Exec(
				tx,
				`
					insert into terminal.tabs(id, session_id, title, title_pinned, position, active_pane_id, layout)
					values (:id, :session_id, :title, :title_pinned, :position, :active_pane_id, :layout)
				`,
				db.Params{
					"id":             tabId,
					"session_id":     sessionRow.Id,
					"title":          tabTitle,
					"title_pinned":   tabPinned,
					"position":       nextPos,
					"active_pane_id": paneId,
					"layout":         layoutBytes,
				},
			)

			db.Exec(
				tx,
				`
					insert into terminal.panes(id, session_id, tab_id, rank, title, title_pinned, backend_handle)
					values (:id, :session_id, :tab_id, :rank, :title, false, :backend_handle)
				`,
				db.Params{
					"id":             paneId,
					"session_id":     sessionRow.Id,
					"tab_id":         tabId,
					"rank":           rank,
					"title":          terminalSessionDefaultPaneTitle(job, rank),
					"backend_handle": handle,
				},
			)

			row, _ := db.Get[terminalSessionRow](
				tx,
				`
					update terminal.sessions
					set
						active_tab_id = :active_tab_id,
						revision = revision + 1,
						updated_at = now(),
						last_accessed_at = now()
					where id = :id
					returning
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
				`,
				db.Params{
					"id":            sessionRow.Id,
					"active_tab_id": tabId,
				},
			)
			return row
		})

		return terminalSessionBuildDetail(updatedRow, job), nil
	})

	orcapi.JobsTerminalSessionSplitPane.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionSplitPaneRequest) (orcapi.TerminalSessionDetail, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionDetail{}, featureErr
		}

		direction := request.Direction
		if direction != orcapi.TerminalSessionSplitHorizontal && direction != orcapi.TerminalSessionSplitVertical {
			return orcapi.TerminalSessionDetail{}, util.HttpErr(http.StatusBadRequest, "invalid split direction")
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionDetail{}, err
		}

		var updatedRow terminalSessionRow
		var splitErr *util.HttpError

		db.NewTx0(func(tx *db.Transaction) {
			targetPane, foundPane := db.Get[terminalPaneRow](
				tx,
				`
					select
						id,
						session_id,
						tab_id,
						rank,
						title,
						title_pinned,
						backend_handle,
						created_at
					from terminal.panes
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":         request.TargetPaneId,
					"session_id": sessionRow.Id,
				},
			)
			if !foundPane {
				splitErr = util.HttpErr(http.StatusNotFound, "target pane not found")
				return
			}

			tabRow, foundTab := db.Get[terminalTabRow](
				tx,
				`
					select
						id,
						session_id,
						title,
						title_pinned,
						position,
						active_pane_id,
						layout
					from terminal.tabs
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":         targetPane.TabId,
					"session_id": sessionRow.Id,
				},
			)
			if !foundTab {
				splitErr = util.HttpErr(http.StatusNotFound, "tab not found")
				return
			}

			layout := orcapi.TerminalSessionLayoutNode{}
			unmarshalErr := json.Unmarshal(tabRow.Layout, &layout)
			if unmarshalErr != nil {
				layout = orcapi.TerminalSessionLayoutNode{
					Kind:   "pane",
					PaneId: targetPane.Id,
				}
			}

			newPaneId := terminalSessionNewId("pane")
			handle := terminalSessionNewHandle()
			rank := targetPane.Rank
			if request.Rank.Present {
				rank = terminalSessionClampRank(request.Rank.Value, job.Specification.Replicas)
			}

			replaced := terminalSessionLayoutSplit(&layout, targetPane.Id, newPaneId, direction, request.PlaceFirst)
			if !replaced {
				splitErr = util.HttpErr(http.StatusConflict, "target pane missing from tab layout")
				return
			}

			layoutBytes, _ := json.Marshal(layout)

			db.Exec(
				tx,
				`
					insert into terminal.panes(id, session_id, tab_id, rank, title, title_pinned, backend_handle)
					values (:id, :session_id, :tab_id, :rank, :title, false, :backend_handle)
				`,
				db.Params{
					"id":             newPaneId,
					"session_id":     sessionRow.Id,
					"tab_id":         tabRow.Id,
					"rank":           rank,
					"title":          terminalSessionDefaultPaneTitle(job, rank),
					"backend_handle": handle,
				},
			)

			db.Exec(
				tx,
				`
					update terminal.tabs
					set
						active_pane_id = :active_pane_id,
						layout = :layout
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":             tabRow.Id,
					"session_id":     sessionRow.Id,
					"active_pane_id": newPaneId,
					"layout":         layoutBytes,
				},
			)

			updatedRow, _ = db.Get[terminalSessionRow](
				tx,
				`
					update terminal.sessions
					set
						active_tab_id = :active_tab_id,
						revision = revision + 1,
						updated_at = now(),
						last_accessed_at = now()
					where id = :id
					returning
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
				`,
				db.Params{
					"id":            sessionRow.Id,
					"active_tab_id": tabRow.Id,
				},
			)
		})

		if splitErr != nil {
			return orcapi.TerminalSessionDetail{}, splitErr
		}

		return terminalSessionBuildDetail(updatedRow, job), nil
	})

	orcapi.JobsTerminalSessionClosePane.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionClosePaneRequest) (orcapi.TerminalSessionCloseResponse, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionCloseResponse{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionCloseResponse{}, err
		}

		var targetHandle orcapi.TerminalSessionProviderHandleTarget
		var hasHandle bool
		var sessionDeleted bool
		var updatedRow terminalSessionRow

		db.NewTx0(func(tx *db.Transaction) {
			pane, foundPane := db.Get[terminalPaneRow](
				tx,
				`
					select
						id,
						session_id,
						tab_id,
						rank,
						title,
						title_pinned,
						backend_handle,
						created_at
					from terminal.panes
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":         request.PaneId,
					"session_id": sessionRow.Id,
				},
			)
			if !foundPane {
				return
			}

			targetHandle = orcapi.TerminalSessionProviderHandleTarget{
				Rank:          pane.Rank,
				BackendHandle: pane.BackendHandle,
			}
			hasHandle = true

			db.Exec(
				tx,
				`
					delete from terminal.panes
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":         pane.Id,
					"session_id": sessionRow.Id,
				},
			)

			remainingPanes := db.Select[terminalPaneRow](
				tx,
				`
					select
						id,
						session_id,
						tab_id,
						rank,
						title,
						title_pinned,
						backend_handle,
						created_at
					from terminal.panes
					where tab_id = :tab_id
					order by created_at asc
				`,
				db.Params{
					"tab_id": pane.TabId,
				},
			)

			if len(remainingPanes) == 0 {
				db.Exec(
					tx,
					`
						delete from terminal.tabs
						where
							id = :id
							and session_id = :session_id
					`,
					db.Params{
						"id":         pane.TabId,
						"session_id": sessionRow.Id,
					},
				)

				remainingTabs := db.Select[terminalTabRow](
					tx,
					`
						select
							id,
							session_id,
							title,
							title_pinned,
							position,
							active_pane_id,
							layout
						from terminal.tabs
						where session_id = :session_id
						order by position asc
					`,
					db.Params{
						"session_id": sessionRow.Id,
					},
				)

				if len(remainingTabs) == 0 {
					db.Exec(
						tx,
						`
							delete from terminal.sessions
							where id = :id
						`,
						db.Params{
							"id": sessionRow.Id,
						},
					)
					sessionDeleted = true
					return
				}

				nextActiveTabId := sessionRow.ActiveTabId.String
				activeStillPresent := false
				for _, tab := range remainingTabs {
					if tab.Id == nextActiveTabId {
						activeStillPresent = true
						break
					}
				}
				if !activeStillPresent {
					nextActiveTabId = remainingTabs[0].Id
				}

				updatedRow, _ = db.Get[terminalSessionRow](
					tx,
					`
						update terminal.sessions
						set
							active_tab_id = :active_tab_id,
							revision = revision + 1,
							updated_at = now(),
							last_accessed_at = now()
						where id = :id
						returning
							id,
							job_id,
							provider_id,
							created_by,
							title,
							active_tab_id,
							revision,
							created_at,
							updated_at,
							last_accessed_at
					`,
					db.Params{
						"id":            sessionRow.Id,
						"active_tab_id": nextActiveTabId,
					},
				)
				return
			}

			tabRow, _ := db.Get[terminalTabRow](
				tx,
				`
					select
						id,
						session_id,
						title,
						title_pinned,
						position,
						active_pane_id,
						layout
					from terminal.tabs
					where id = :id
				`,
				db.Params{
					"id": pane.TabId,
				},
			)

			layout := orcapi.TerminalSessionLayoutNode{}
			_ = json.Unmarshal(tabRow.Layout, &layout)
			nextLayout, exists := terminalSessionLayoutRemove(layout, pane.Id)
			if !exists {
				nextLayout = orcapi.TerminalSessionLayoutNode{
					Kind:   "pane",
					PaneId: remainingPanes[0].Id,
				}
			}

			nextActivePane := tabRow.ActivePaneId.String
			if nextActivePane == pane.Id || nextActivePane == "" {
				nextActivePane = remainingPanes[0].Id
			}

			layoutBytes, _ := json.Marshal(nextLayout)
			db.Exec(
				tx,
				`
					update terminal.tabs
					set
						active_pane_id = :active_pane_id,
						layout = :layout
					where id = :id
				`,
				db.Params{
					"id":             pane.TabId,
					"active_pane_id": nextActivePane,
					"layout":         layoutBytes,
				},
			)

			updatedRow, _ = db.Get[terminalSessionRow](
				tx,
				`
					update terminal.sessions
					set
						revision = revision + 1,
						updated_at = now(),
						last_accessed_at = now()
					where id = :id
					returning
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
				`,
				db.Params{
					"id": sessionRow.Id,
				},
			)
		})

		if hasHandle {
			terminalSessionKillHandlesBestEffort(info.Actor.Username, job, []orcapi.TerminalSessionProviderHandleTarget{targetHandle})
		}

		if sessionDeleted {
			return orcapi.TerminalSessionCloseResponse{
				SessionDeleted: true,
			}, nil
		}

		if updatedRow.Id == "" {
			updatedRow = sessionRow
		}
		detail := terminalSessionBuildDetail(updatedRow, job)
		return orcapi.TerminalSessionCloseResponse{
			SessionDeleted: false,
			Session:        &detail,
		}, nil
	})

	orcapi.JobsTerminalSessionCloseTab.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionCloseTabRequest) (orcapi.TerminalSessionCloseResponse, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionCloseResponse{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionCloseResponse{}, err
		}

		var handles []orcapi.TerminalSessionProviderHandleTarget
		var sessionDeleted bool
		var updatedRow terminalSessionRow

		db.NewTx0(func(tx *db.Transaction) {
			panes := db.Select[terminalPaneRow](
				tx,
				`
					select
						id,
						session_id,
						tab_id,
						rank,
						title,
						title_pinned,
						backend_handle,
						created_at
					from terminal.panes
					where
						tab_id = :tab_id
						and session_id = :session_id
				`,
				db.Params{
					"tab_id":     request.TabId,
					"session_id": sessionRow.Id,
				},
			)
			for _, p := range panes {
				handles = append(handles, orcapi.TerminalSessionProviderHandleTarget{
					Rank:          p.Rank,
					BackendHandle: p.BackendHandle,
				})
			}

			db.Exec(
				tx,
				`
					delete from terminal.tabs
					where
						id = :id
						and session_id = :session_id
				`,
				db.Params{
					"id":         request.TabId,
					"session_id": sessionRow.Id,
				},
			)

			remainingTabs := db.Select[terminalTabRow](
				tx,
				`
					select
						id,
						session_id,
						title,
						title_pinned,
						position,
						active_pane_id,
						layout
					from terminal.tabs
					where session_id = :session_id
					order by position asc
				`,
				db.Params{
					"session_id": sessionRow.Id,
				},
			)

			if len(remainingTabs) == 0 {
				db.Exec(
					tx,
					`
						delete from terminal.sessions
						where id = :id
					`,
					db.Params{
						"id": sessionRow.Id,
					},
				)
				sessionDeleted = true
				return
			}

			nextActiveTabId := sessionRow.ActiveTabId.String
			activeStillPresent := false
			for _, tab := range remainingTabs {
				if tab.Id == nextActiveTabId {
					activeStillPresent = true
					break
				}
			}
			if !activeStillPresent {
				nextActiveTabId = remainingTabs[0].Id
			}

			updatedRow, _ = db.Get[terminalSessionRow](
				tx,
				`
					update terminal.sessions
					set
						active_tab_id = :active_tab_id,
						revision = revision + 1,
						updated_at = now(),
						last_accessed_at = now()
					where id = :id
					returning
						id,
						job_id,
						provider_id,
						created_by,
						title,
						active_tab_id,
						revision,
						created_at,
						updated_at,
						last_accessed_at
				`,
				db.Params{
					"id":            sessionRow.Id,
					"active_tab_id": nextActiveTabId,
				},
			)
		})

		if len(handles) > 0 {
			terminalSessionKillHandlesBestEffort(info.Actor.Username, job, handles)
		}

		if sessionDeleted {
			return orcapi.TerminalSessionCloseResponse{
				SessionDeleted: true,
			}, nil
		}

		detail := terminalSessionBuildDetail(updatedRow, job)
		return orcapi.TerminalSessionCloseResponse{
			SessionDeleted: false,
			Session:        &detail,
		}, nil
	})

	orcapi.JobsTerminalSessionShutdown.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionShutdownRequest) (util.Empty, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return util.Empty{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return util.Empty{}, err
		}

		handles := db.NewTx(func(tx *db.Transaction) []orcapi.TerminalSessionProviderHandleTarget {
			panes := db.Select[terminalPaneRow](
				tx,
				`
					select
						id,
						session_id,
						tab_id,
						rank,
						title,
						title_pinned,
						backend_handle,
						created_at
					from terminal.panes
					where session_id = :session_id
				`,
				db.Params{
					"session_id": sessionRow.Id,
				},
			)

			targets := make([]orcapi.TerminalSessionProviderHandleTarget, 0, len(panes))
			for _, pane := range panes {
				targets = append(targets, orcapi.TerminalSessionProviderHandleTarget{
					Rank:          pane.Rank,
					BackendHandle: pane.BackendHandle,
				})
			}

			db.Exec(
				tx,
				`
					delete from terminal.sessions
					where id = :id
				`,
				db.Params{
					"id": sessionRow.Id,
				},
			)
			return targets
		})

		if len(handles) > 0 {
			terminalSessionKillHandlesBestEffort(info.Actor.Username, job, handles)
		}

		return util.Empty{}, nil
	})

	orcapi.JobsTerminalSessionConnect.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionConnectRequest) (orcapi.TerminalSessionConnectResponse, *util.HttpError) {
		featureErr := coreutil.FeatureIsEnabled(info.Actor, fndapi.FeatureTerminalV2)
		if featureErr != nil {
			return orcapi.TerminalSessionConnectResponse{}, featureErr
		}

		sessionRow, job, err := terminalSessionLoadOwned(info.Actor, request.SessionId)
		if err != nil {
			return orcapi.TerminalSessionConnectResponse{}, err
		}

		provider := job.Specification.Product.Provider
		resp, callErr := InvokeProvider(
			provider,
			orcapi.JobsProviderTerminalSessionConnect,
			orcapi.TerminalSessionProviderConnectRequest{
				SessionId: sessionRow.Id,
				Job:       job,
				Username:  info.Actor.Username,
			},
			ProviderCallOpts{
				Username: util.OptValue(info.Actor.Username),
				Reason:   util.OptValue("open persistent terminal session"),
				Timeout:  util.OptValue(15 * time.Second),
			},
		)
		if callErr != nil {
			return orcapi.TerminalSessionConnectResponse{}, callErr
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update terminal.sessions
					set last_accessed_at = now()
					where id = :id
				`,
				db.Params{
					"id": sessionRow.Id,
				},
			)
		})

		domain, _ := ProviderDomain(provider)
		if resp.DomainOverride != "" {
			domain = resp.DomainOverride
		}

		return orcapi.TerminalSessionConnectResponse{
			ProviderDomain: domain,
			ProviderId:     provider,
			Token:          resp.Token,
			WsPath:         fmt.Sprintf("/ucloud/%s/terminal-ws", provider),
		}, nil
	})
}

func terminalSessionDeleteByJobIdBatch(b *db.Batch, jobResourceId ResourceId) {
	db.BatchExec(
		b,
		`
			delete from terminal.sessions
			where job_id = :job_id
		`,
		db.Params{
			"job_id": int64(jobResourceId),
		},
	)
}

func terminalSessionValidateJob(actor rpc.Actor, jobId string, rank int) (orcapi.Job, ResourceId, *util.HttpError) {
	parsedId := ResourceParseId(jobId)
	if parsedId <= 0 {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusBadRequest, "invalid job id")
	}

	resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
		actor,
		jobType,
		parsedId,
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{
			IncludeProduct: true,
		},
	)
	if err != nil {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", jobId)
	}

	if resc.Status.State != orcapi.JobStateRunning {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusBadRequest, "job is not running")
	}

	app := resc.Status.ResolvedApplication
	if !app.Present || !app.Value.Invocation.Tool.Tool.Present {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusBadRequest, "the job application is unavailable")
	}

	appBackend := app.Value.Invocation.Tool.Tool.Value.Description.Backend
	support, ok := SupportByProduct[orcapi.JobSupport](jobType, resc.Specification.Product)
	if !ok || !support.Has(jobFeatureTerminalByBackend[appBackend]) {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusBadRequest, "terminal access is not supported by this provider")
	}

	if rank < 0 || (resc.Specification.Replicas > 0 && rank >= resc.Specification.Replicas) {
		return orcapi.Job{}, 0, util.HttpErr(http.StatusBadRequest, "invalid node rank")
	}

	return resc, parsedId, nil
}

func terminalSessionLoadOwned(actor rpc.Actor, sessionId string) (terminalSessionRow, orcapi.Job, *util.HttpError) {
	if strings.TrimSpace(sessionId) == "" {
		return terminalSessionRow{}, orcapi.Job{}, util.HttpErr(http.StatusBadRequest, "session id is required")
	}

	var sessionRow terminalSessionRow
	var found bool
	db.NewTx0(func(tx *db.Transaction) {
		sessionRow, found = db.Get[terminalSessionRow](
			tx,
			`
				select
					id,
					job_id,
					provider_id,
					created_by,
					title,
					active_tab_id,
					revision,
					created_at,
					updated_at,
					last_accessed_at
				from terminal.sessions
				where
					id = :id
					and created_by = :username
			`,
			db.Params{
				"id":       sessionId,
				"username": actor.Username,
			},
		)
	})
	if !found {
		return terminalSessionRow{}, orcapi.Job{}, util.HttpErr(http.StatusNotFound, "terminal session not found")
	}

	job, _, err := terminalSessionValidateJob(actor, strconv.FormatInt(sessionRow.JobId, 10), 0)
	if err != nil {
		if err.StatusCode == http.StatusBadRequest || err.StatusCode == http.StatusNotFound {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						delete from terminal.sessions
						where id = :id
					`,
					db.Params{
						"id": sessionRow.Id,
					},
				)
			})
		}
		return terminalSessionRow{}, orcapi.Job{}, err
	}

	return sessionRow, job, nil
}

func terminalSessionCreateInDb(username string, job orcapi.Job, jobResourceId ResourceId, requestedTitle string, rank int) terminalSessionRow {
	clampedRank := terminalSessionClampRank(rank, job.Specification.Replicas)

	return db.NewTx(func(tx *db.Transaction) terminalSessionRow {
		countRow, _ := db.Get[struct{ Cnt int }](
			tx,
			`
				select count(*) as cnt
				from terminal.sessions
				where
					created_by = :username
					and job_id = :job_id
			`,
			db.Params{
				"username": username,
				"job_id":   int64(jobResourceId),
			},
		)

		title := strings.TrimSpace(requestedTitle)
		if title == "" {
			title = fmt.Sprintf("Session %d", countRow.Cnt+1)
		}

		sessionId := terminalSessionNewId("ts")
		tabId := terminalSessionNewId("tab")
		paneId := terminalSessionNewId("pane")
		handle := terminalSessionNewHandle()

		layoutBytes, _ := json.Marshal(orcapi.TerminalSessionLayoutNode{
			Kind:   "pane",
			PaneId: paneId,
		})

		row, _ := db.Get[terminalSessionRow](
			tx,
			`
				insert into terminal.sessions(id, job_id, provider_id, created_by, title, active_tab_id)
				values (:id, :job_id, :provider_id, :created_by, :title, :active_tab_id)
				returning
					id,
					job_id,
					provider_id,
					created_by,
					title,
					active_tab_id,
					revision,
					created_at,
					updated_at,
					last_accessed_at
			`,
			db.Params{
				"id":            sessionId,
				"job_id":        int64(jobResourceId),
				"provider_id":   job.Specification.Product.Provider,
				"created_by":    username,
				"title":         title,
				"active_tab_id": tabId,
			},
		)

		db.Exec(
			tx,
			`
				insert into terminal.tabs(id, session_id, title, title_pinned, position, active_pane_id, layout)
				values (:id, :session_id, :title, false, 0, :active_pane_id, :layout)
			`,
			db.Params{
				"id":             tabId,
				"session_id":     sessionId,
				"title":          "Tab 1",
				"active_pane_id": paneId,
				"layout":         layoutBytes,
			},
		)

		db.Exec(
			tx,
			`
				insert into terminal.panes(id, session_id, tab_id, rank, title, title_pinned, backend_handle)
				values (:id, :session_id, :tab_id, :rank, :title, false, :backend_handle)
			`,
			db.Params{
				"id":             paneId,
				"session_id":     sessionId,
				"tab_id":         tabId,
				"rank":           clampedRank,
				"title":          terminalSessionDefaultPaneTitle(job, clampedRank),
				"backend_handle": handle,
			},
		)

		return row
	})
}

func terminalSessionBuildDetail(sessionRow terminalSessionRow, job orcapi.Job) orcapi.TerminalSessionDetail {
	var tabRows []terminalTabRow
	var paneRows []terminalPaneRow
	db.NewTx0(func(tx *db.Transaction) {
		tabRows = db.Select[terminalTabRow](
			tx,
			`
				select
					id,
					session_id,
					title,
					title_pinned,
					position,
					active_pane_id,
					layout
				from terminal.tabs
				where session_id = :session_id
				order by position asc, id asc
			`,
			db.Params{
				"session_id": sessionRow.Id,
			},
		)
		paneRows = db.Select[terminalPaneRow](
			tx,
			`
				select
					id,
					session_id,
					tab_id,
					rank,
					title,
					title_pinned,
					backend_handle,
					created_at
				from terminal.panes
				where session_id = :session_id
				order by created_at asc, id asc
			`,
			db.Params{
				"session_id": sessionRow.Id,
			},
		)
	})

	panesByTab := make(map[string][]orcapi.TerminalSessionPane, len(tabRows))
	for _, pane := range paneRows {
		panesByTab[pane.TabId] = append(panesByTab[pane.TabId], orcapi.TerminalSessionPane{
			Id:            pane.Id,
			TabId:         pane.TabId,
			Rank:          pane.Rank,
			Title:         pane.Title,
			TitlePinned:   pane.TitlePinned,
			BackendHandle: pane.BackendHandle,
			CreatedAt:     fndapi.Timestamp(pane.CreatedAt),
		})
	}

	tabs := make([]orcapi.TerminalSessionTab, 0, len(tabRows))
	for _, tab := range tabRows {
		layout := orcapi.TerminalSessionLayoutNode{}
		_ = json.Unmarshal(tab.Layout, &layout)
		panes := panesByTab[tab.Id]
		if panes == nil {
			panes = []orcapi.TerminalSessionPane{}
		}
		tabs = append(tabs, orcapi.TerminalSessionTab{
			Id:           tab.Id,
			Title:        tab.Title,
			TitlePinned:  tab.TitlePinned,
			Position:     tab.Position,
			ActivePaneId: tab.ActivePaneId.String,
			Layout:       layout,
			Panes:        panes,
		})
	}

	jobTitle := strings.TrimSpace(job.Specification.Name)
	if jobTitle == "" {
		jobTitle = fmt.Sprintf("Job %s", job.Id)
	}
	appTitle := job.Specification.Application.Name
	if job.Status.ResolvedApplication.Present && job.Status.ResolvedApplication.Value.Metadata.Title != "" {
		appTitle = job.Status.ResolvedApplication.Value.Metadata.Title
	}

	return orcapi.TerminalSessionDetail{
		Id:               sessionRow.Id,
		JobId:            job.Id,
		JobTitle:         jobTitle,
		ApplicationName:  job.Specification.Application.Name,
		ApplicationTitle: appTitle,
		ProviderId:       sessionRow.ProviderId,
		Replicas:         job.Specification.Replicas,
		Title:            sessionRow.Title,
		ActiveTabId:      sessionRow.ActiveTabId.String,
		Revision:         sessionRow.Revision,
		CreatedAt:        fndapi.Timestamp(sessionRow.CreatedAt),
		UpdatedAt:        fndapi.Timestamp(sessionRow.UpdatedAt),
		LastAccessedAt:   fndapi.Timestamp(sessionRow.LastAccessedAt),
		Tabs:             tabs,
	}
}

func terminalSessionKillHandlesBestEffort(username string, job orcapi.Job, handles []orcapi.TerminalSessionProviderHandleTarget) {
	provider := job.Specification.Product.Provider
	_, err := InvokeProvider(
		provider,
		orcapi.JobsProviderTerminalKillHandles,
		orcapi.TerminalSessionProviderKillHandlesRequest{
			Job:     job,
			Handles: handles,
		},
		ProviderCallOpts{
			Username: util.OptValue(username),
			Reason:   util.OptValue("terminate persistent terminal sub-session"),
			Timeout:  util.OptValue(10 * time.Second),
		},
	)
	if err != nil {
		log.Warn("Failed to terminate terminal handles on provider %s for job %s: %v", provider, job.Id, err)
	}
}

func terminalSessionNewId(prefix string) string {
	return prefix + "_" + util.RandomTokenNoTs(16)
}

func terminalSessionNewHandle() string {
	return "uc_" + util.RandomTokenNoTs(12)
}

func terminalSessionClampRank(rank int, replicas int) int {
	if rank < 0 {
		return 0
	}
	if replicas > 0 && rank >= replicas {
		return 0
	}
	return rank
}

func terminalSessionDefaultPaneTitle(job orcapi.Job, rank int) string {
	if job.Specification.Replicas > 1 {
		return fmt.Sprintf("Shell (Node %d)", rank+1)
	}
	return "Shell"
}

func terminalSessionNormalizeLayout(node *orcapi.TerminalSessionLayoutNode) {
	if node == nil || node.Kind != "split" {
		return
	}
	if math.IsNaN(node.Ratio) || math.IsInf(node.Ratio, 0) || node.Ratio == 0 {
		node.Ratio = 0.5
	}
	if node.Ratio < 0.1 {
		node.Ratio = 0.1
	}
	if node.Ratio > 0.9 {
		node.Ratio = 0.9
	}
	terminalSessionNormalizeLayout(node.First)
	terminalSessionNormalizeLayout(node.Second)
}

func terminalSessionLayoutSplit(
	node *orcapi.TerminalSessionLayoutNode,
	targetPaneId string,
	newPaneId string,
	direction orcapi.TerminalSessionSplitDirection,
	placeFirst bool,
) bool {
	if node == nil {
		return false
	}
	if node.Kind == "pane" {
		if node.PaneId != targetPaneId {
			return false
		}
		existingLeaf := &orcapi.TerminalSessionLayoutNode{
			Kind:   "pane",
			PaneId: targetPaneId,
		}
		newLeaf := &orcapi.TerminalSessionLayoutNode{
			Kind:   "pane",
			PaneId: newPaneId,
		}
		node.Kind = "split"
		node.PaneId = ""
		node.Direction = direction
		node.Ratio = 0.5
		if placeFirst {
			node.First = newLeaf
			node.Second = existingLeaf
		} else {
			node.First = existingLeaf
			node.Second = newLeaf
		}
		return true
	}

	if terminalSessionLayoutSplit(node.First, targetPaneId, newPaneId, direction, placeFirst) {
		return true
	}
	return terminalSessionLayoutSplit(node.Second, targetPaneId, newPaneId, direction, placeFirst)
}

func terminalSessionLayoutRemove(node orcapi.TerminalSessionLayoutNode, targetPaneId string) (orcapi.TerminalSessionLayoutNode, bool) {
	if node.Kind == "pane" {
		if node.PaneId == targetPaneId {
			return orcapi.TerminalSessionLayoutNode{}, false
		}
		return node, true
	}

	if node.First == nil || node.Second == nil {
		return orcapi.TerminalSessionLayoutNode{}, false
	}

	firstNext, firstKeep := terminalSessionLayoutRemove(*node.First, targetPaneId)
	secondNext, secondKeep := terminalSessionLayoutRemove(*node.Second, targetPaneId)

	if !firstKeep && !secondKeep {
		return orcapi.TerminalSessionLayoutNode{}, false
	}
	if !firstKeep {
		return secondNext, true
	}
	if !secondKeep {
		return firstNext, true
	}

	return orcapi.TerminalSessionLayoutNode{
		Kind:      "split",
		Direction: node.Direction,
		Ratio:     node.Ratio,
		First:     &firstNext,
		Second:    &secondNext,
	}, true
}
