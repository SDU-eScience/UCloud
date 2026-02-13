package accounting

import (
	"cmp"
	"database/sql"
	"encoding/json"
	"fmt"
	"slices"
	"strconv"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// Database persistence layer
// =====================================================================================================================

func grantsLoad(id accGrantId, prefetchHint []accGrantId) {
	if grantGlobals.Testing.Enabled {
		return
	}

	prefetchList := prefetchHint
	requiredPrefetchIdx := slices.Index(prefetchList, id)
	if requiredPrefetchIdx == -1 {
		copiedList := make([]accGrantId, len(prefetchList))
		copy(copiedList, prefetchList)
		prefetchList = copiedList

		prefetchList = append(prefetchList, id)
		requiredPrefetchIdx = len(prefetchList) - 1
	}

	var awarded map[accGrantId]util.Empty

	apps := db.NewTx(func(tx *db.Transaction) []accapi.GrantApplication {
		tx.NoDevResetThisIsNotAHackIPromise = true

		awarded = map[accGrantId]util.Empty{}

		b := db.BatchNew(tx)
		appsPromise := db.BatchSelect[struct {
			Id           int
			OverallState string
			RequestedBy  string
			CreatedAt    time.Time
			UpdatedAt    time.Time
			Synchronized bool
		}](
			b,
			`
				select id, overall_state, requested_by, created_at, updated_at, synchronized
				from "grant".applications app
				where id = some(:ids)
		    `,
			db.Params{
				"ids": prefetchList,
			},
		)

		commentsPromise := db.BatchSelect[struct {
			ApplicationId int
			Comment       string
			PostedBy      string
			CreatedAt     time.Time
			CommentId     int
		}](
			b,
			`
				select application_id, comment, posted_by, created_at, id as comment_id
				from "grant".comments
				where application_id = some(:ids)
				order by created_at
		    `,
			db.Params{
				"ids": prefetchList,
			},
		)

		revisionsPromise := db.BatchSelect[struct {
			ApplicationId   int
			Form            string
			ParentProjectId sql.Null[string]
			Recipient       string
			RecipientType   string
			ReferenceIds    []string
			RevisionNumber  int
			CreatedAt       time.Time
			UpdatedBy       string
			RevisionComment sql.Null[string]
			GrantStart      time.Time
			GrantEnd        time.Time
			ProjectTitle    sql.Null[string]
			PiUsername      sql.Null[string]
		}](
			b,
			`
				select
					f.application_id,
					f.form,
					f.parent_project_id,
					f.recipient,
					f.recipient_type,
					f.reference_ids,
					f.revision_number,
					r.created_at,
					r.updated_by,
					r.revision_comment,
					r.grant_start,
					r.grant_end,
					p.title as project_title,
					pi.username as pi_username
				from 
					"grant".forms f
					join "grant".revisions r on 
						f.application_id = r.application_id 
						and f.revision_number = r.revision_number
					left join project.projects p on p.id = f.recipient
					left join project.project_members pi on p.id = pi.project_id and pi.role = 'PI'
				where
					f.application_id = some(:ids)
		    `,
			db.Params{
				"ids": prefetchList,
			},
		)

		approvalsPromise := db.BatchSelect[struct {
			ApplicationId int
			ProjectId     string
			ProjectTitle  sql.Null[string]
			State         string
			UpdatedBy     sql.Null[string]
			LastUpdate    time.Time
		}](
			b,
			`
				select application_id, project_id, project_title, state, updated_by, last_update
				from "grant".grant_giver_approvals 
				where application_id = some(:ids)
		    `,
			db.Params{
				"ids": prefetchList,
			},
		)

		resourcesPromise := db.BatchSelect[struct {
			ApplicationId    int
			CreditsRequested int
			Category         string
			Provider         string
			StartDate        sql.Null[time.Time]
			EndDate          sql.Null[time.Time]
			GrantGiver       string
			RevisionNumber   int
		}](
			b,
			`
				select 
					application_id, coalesce(credits_requested, quota_requested_bytes) as credits_requested,
					pc.category, pc.provider, start_date, end_date, grant_giver, revision_number
				from
					"grant".requested_resources rr
					join accounting.product_categories pc on rr.product_category = pc.id
				where
					application_id = some(:ids)
		    `,
			db.Params{
				"ids": prefetchList,
			},
		)

		db.BatchSend(b)

		apps := *appsPromise
		comments := *commentsPromise
		revisions := *revisionsPromise
		approvals := *approvalsPromise
		resources := *resourcesPromise

		appsById := map[int]*accapi.GrantApplication{}
		for _, app := range apps {
			appsById[app.Id] = &accapi.GrantApplication{
				Id:              util.IntOrString{Value: fmt.Sprint(app.Id)},
				CreatedBy:       app.RequestedBy,
				CreatedAt:       fndapi.Timestamp(app.CreatedAt),
				UpdatedAt:       fndapi.Timestamp(app.UpdatedAt),
				CurrentRevision: accapi.GrantRevision{},
				Status: accapi.GrantStatus{
					OverallState: accapi.GrantApplicationState(app.OverallState),
				},
			}
		}

		for _, comment := range comments {
			app, ok := appsById[comment.ApplicationId]
			if !ok {
				continue
			}

			app.Status.Comments = append(app.Status.Comments, accapi.GrantComment{
				Id:        util.IntOrString{Value: fmt.Sprint(comment.CommentId)},
				Username:  comment.PostedBy,
				CreatedAt: fndapi.Timestamp(comment.CreatedAt),
				Comment:   comment.Comment,
			})
		}

		for _, revision := range revisions {
			app, ok := appsById[revision.ApplicationId]
			if !ok {
				continue
			}

			app.Status.Revisions = append(app.Status.Revisions, accapi.GrantRevision{
				CreatedAt:      fndapi.Timestamp(revision.CreatedAt),
				UpdatedBy:      revision.UpdatedBy,
				RevisionNumber: revision.RevisionNumber,
				Document: accapi.GrantDocument{
					Recipient:          accapi.RecipientFromReference(accapi.RecipientType(revision.RecipientType), revision.Recipient),
					AllocationRequests: nil,
					Form: accapi.Form{
						Type: accapi.FormTypePlainText,
						Text: revision.Form,
					},
					ReferenceIds:    util.OptValue(revision.ReferenceIds),
					RevisionComment: util.SqlNullToOpt(revision.RevisionComment),
					AllocationPeriod: util.OptValue[accapi.Period](accapi.Period{
						Start: util.OptValue[fndapi.Timestamp](fndapi.Timestamp(revision.GrantStart)),
						End:   util.OptValue[fndapi.Timestamp](fndapi.Timestamp(revision.GrantEnd)),
					}),
				},
			})

			if revision.ProjectTitle.Valid {
				app.Status.ProjectTitle.Set(revision.ProjectTitle.V)
				app.Status.ProjectPI = revision.PiUsername.V
			}
		}

		for _, approval := range approvals {
			app, ok := appsById[approval.ApplicationId]
			if !ok {
				continue
			}

			app.Status.StateBreakdown = append(app.Status.StateBreakdown, accapi.GrantGiverApprovalState{
				ProjectId: approval.ProjectId,
				State:     accapi.GrantApplicationState(approval.State),
			})
		}

		for _, resource := range resources {
			app, ok := appsById[resource.ApplicationId]
			if !ok {
				continue
			}

			for i := range app.Status.Revisions {
				revision := &app.Status.Revisions[i]
				if revision.RevisionNumber == resource.RevisionNumber {
					period := revision.Document.AllocationPeriod
					if !period.Present {
						start := util.SqlNullToOpt(resource.StartDate)
						if start.Present {
							period.Value.Start.Set(fndapi.Timestamp(start.Value))
						}

						end := util.SqlNullToOpt(resource.EndDate)
						if end.Present {
							period.Value.End.Set(fndapi.Timestamp(end.Value))
						}

						period.Present = true
					}

					revision.Document.AllocationRequests = append(revision.Document.AllocationRequests, accapi.AllocationRequest{
						Category:         resource.Category,
						Provider:         resource.Provider,
						GrantGiver:       resource.GrantGiver,
						BalanceRequested: util.OptValue(int64(resource.CreditsRequested)),
						Period:           period.Value,
					})
					break
				}
			}
		}

		var result []accapi.GrantApplication
		for _, app := range appsById {
			slices.SortFunc(app.Status.Revisions, func(a, b accapi.GrantRevision) int {
				return cmp.Compare(a.RevisionNumber, b.RevisionNumber)
			})

			if len(app.Status.Revisions) > 0 {
				app.CurrentRevision = app.Status.Revisions[len(app.Status.Revisions)-1]
			}

			app.Status.Comments = util.NonNilSlice(app.Status.Comments)
			app.Status.Revisions = util.NonNilSlice(app.Status.Revisions)

			result = append(result, *app)
		}

		for _, row := range apps {
			if row.Synchronized {
				awarded[accGrantId(row.Id)] = util.Empty{}
			}
		}

		return result
	})

	for _, appLoop := range apps {
		app := appLoop
		numericId, _ := strconv.ParseInt(app.Id.Value, 10, 64)
		grantId := accGrantId(numericId)
		b := grantGetAppBucket(grantId)

		b.Mu.Lock()

		if _, hasBeenLoaded := b.Applications[grantId]; !hasBeenLoaded {
			_, isAwarded := awarded[grantId]
			resultApp := &grantApplication{
				Application: &app,
				Awarded:     isAwarded && app.Status.OverallState == accapi.GrantApplicationStateApproved,
			}
			b.Applications[grantId] = resultApp
			go grantAddToSearchIndex(resultApp)
		}
		b.Mu.Unlock()
	}
}

func grantsLoadUnawarded() {
	if grantGlobals.Testing.Enabled {
		return
	}

	var toLoad []accGrantId
	db.NewTx0(func(tx *db.Transaction) {
		maxId, ok := db.Get[struct{ Id int64 }](
			tx,
			`
				select coalesce(max(id), 0) as id
				from "grant".applications
		    `,
			db.Params{},
		)

		if ok {
			grantGlobals.GrantIdAcc.Store(maxId.Id)
		}

		maxId, ok = db.Get[struct{ Id int64 }](
			tx,
			`
				select coalesce(max(id), 0) as id
				from "grant".comments
		    `,
			db.Params{},
		)

		if ok {
			grantGlobals.CommentIdAcc.Store(maxId.Id)
		}

		unawarded := db.Select[struct{ Id int64 }](
			tx,
			`
				select id
				from "grant".applications
				where
					overall_state = 'APPROVED'
					and synchronized = false
		    `,
			db.Params{},
		)

		toLoad = nil
		for _, row := range unawarded {
			toLoad = append(toLoad, accGrantId(row.Id))
		}
	})

	for _, id := range toLoad {
		grantsLoad(id, toLoad)
	}
}

func grantsLoadSettings() {
	if grantGlobals.Testing.Enabled {
		return
	}

	allSettings := db.NewTx(func(tx *db.Transaction) map[string]accapi.GrantRequestSettings {
		templates := db.Select[struct {
			ProjectId       string
			PersonalProject string
			ExistingProject string
			NewProject      string
		}](
			tx,
			`
				select t.project_id, t.personal_project, t.existing_project, t.new_project
				from "grant".templates t
		    `,
			db.Params{},
		)

		allowFrom := db.Select[struct {
			ProjectId   string
			Type        string
			ApplicantId sql.NullString
		}](
			tx,
			`
				select project_id, type, applicant_id
				from "grant".allow_applications_from
		    `,
			db.Params{},
		)

		excludeFrom := db.Select[struct {
			ProjectId   string
			EmailSuffix string
		}](
			tx,
			`
				select project_id, email_suffix
				from "grant".exclude_applications_from
		    `,
			db.Params{},
		)

		publicGrantGivers := db.Select[struct{ ProjectId string }](
			tx,
			`
				select project_id
				from "grant".is_enabled
		    `,
			db.Params{},
		)

		descriptions := db.Select[struct {
			ProjectId   string
			Description string
		}](
			tx,
			`
				select project_id, coalesce(description, '') as description
				from "grant".descriptions
		    `,
			db.Params{},
		)

		result := map[string]accapi.GrantRequestSettings{}
		for _, template := range templates {
			existing := result[template.ProjectId]
			existing.Templates = accapi.Templates{
				Type:            accapi.TemplatesTypePlainText,
				PersonalProject: template.PersonalProject,
				NewProject:      template.NewProject,
				ExistingProject: template.ExistingProject,
			}

			result[template.ProjectId] = existing
		}

		for _, allow := range allowFrom {
			existing := result[allow.ProjectId]

			criteria := accapi.UserCriteria{}
			switch accapi.UserCriteriaType(allow.Type) {
			case accapi.UserCriteriaTypeWayf:
				criteria = accapi.UserCriteria{
					Type: accapi.UserCriteriaTypeWayf,
					Org:  util.OptValue[string](allow.ApplicantId.String),
				}

			case accapi.UserCriteriaTypeEmail:
				criteria = accapi.UserCriteria{
					Type:   accapi.UserCriteriaTypeEmail,
					Domain: util.OptValue[string](allow.ApplicantId.String),
				}

			case accapi.UserCriteriaTypeAnyone:
				criteria = accapi.UserCriteria{
					Type: accapi.UserCriteriaTypeAnyone,
				}

			default:
				log.Warn("unknown criteria type: %s", allow.Type)
				continue
			}

			existing.AllowRequestsFrom = append(existing.AllowRequestsFrom, criteria)

			result[allow.ProjectId] = existing
		}

		for _, exclude := range excludeFrom {
			existing := result[exclude.ProjectId]

			existing.ExcludeRequestsFrom = append(existing.ExcludeRequestsFrom, accapi.UserCriteria{
				Type:   accapi.UserCriteriaTypeEmail,
				Domain: util.OptValue(exclude.EmailSuffix),
			})

			result[exclude.ProjectId] = existing
		}

		for _, public := range publicGrantGivers {
			existing := result[public.ProjectId]
			existing.Enabled = true
			result[public.ProjectId] = existing

			b := grantGetSettingsBucket(public.ProjectId)
			b.PublicGrantGivers[public.ProjectId] = util.Empty{}
		}

		for _, description := range descriptions {
			existing := result[description.ProjectId]
			existing.Description = description.Description
			result[description.ProjectId] = existing
		}

		// Insert defaults
		// -------------------------------------------------------------------------------------------------------------

		for projectId, settings := range result {
			if settings.Templates.Type == "" {
				settings.Templates = accapi.Templates{
					Type:            accapi.TemplatesTypePlainText,
					PersonalProject: defaultTemplate,
					NewProject:      defaultTemplate,
					ExistingProject: defaultTemplate,
				}

				result[projectId] = settings
			}
		}

		return result
	})

	for projectId, settingsLoop := range allSettings {
		settings := settingsLoop
		b := grantGetSettingsBucket(projectId)
		b.Settings[projectId] = &grantSettings{
			ProjectId: projectId,
			Settings:  &settings,
		}
	}
}

func lGrantsPersistSettings(settings *grantSettings) {
	if grantGlobals.Testing.Enabled {
		return
	}

	s := settings.Settings

	db.NewTx0(func(tx *db.Transaction) {
		if s.Enabled {
			db.Exec(
				tx,
				`
					insert into "grant".is_enabled(project_id)
					values (:project)
					on conflict do nothing
			    `,
				db.Params{
					"project": settings.ProjectId,
				},
			)
		} else {
			db.Exec(
				tx,
				`
					delete from "grant".is_enabled
					where project_id = :project
				`,
				db.Params{
					"project": settings.ProjectId,
				},
			)
		}

		db.Exec(
			tx,
			`
				insert into "grant".descriptions(project_id, description) 
				values (:project, :description)
				on conflict (project_id) do update set
					description = excluded.description
		    `,
			db.Params{
				"project":     settings.ProjectId,
				"description": s.Description,
			},
		)

		db.Exec(
			tx,
			`
				insert into "grant".templates(project_id, personal_project, existing_project, new_project) 
				values (:project, :personal, :existing, :new)
				on conflict (project_id) do update set
					personal_project = excluded.personal_project,
					new_project = excluded.new_project,
					existing_project = excluded.existing_project
		    `,
			db.Params{
				"project":  settings.ProjectId,
				"personal": s.Templates.PersonalProject,
				"new":      s.Templates.NewProject,
				"existing": s.Templates.ExistingProject,
			},
		)

		db.Exec(
			tx,
			`delete from "grant".allow_applications_from where project_id = :project`,
			db.Params{
				"project": settings.ProjectId,
			},
		)

		db.Exec(
			tx,
			`delete from "grant".exclude_applications_from where project_id = :project`,
			db.Params{
				"project": settings.ProjectId,
			},
		)

		var allowType []string
		var allowId []string
		for _, allowFrom := range s.AllowRequestsFrom {
			allowType = append(allowType, string(allowFrom.Type))
			allowId = append(allowId, allowFrom.Domain.GetOrDefault(allowFrom.Org.GetOrDefault("")))
		}

		if len(allowId) > 0 {
			db.Exec(
				tx,
				`
					with data as (
						select 
							:project project, 
							unnest(cast(:type as text[])) type,
							unnest(cast(:applicants as text[])) applicant_id
					)
					insert into "grant".allow_applications_from(project_id, type, applicant_id) 
					select project, type, case when applicant_id = '' then null else applicant_id end
					from data
				`,
				db.Params{
					"project":    settings.ProjectId,
					"type":       allowType,
					"applicants": allowId,
				},
			)
		}

		var excludeEmail []string
		for _, excludeFrom := range s.ExcludeRequestsFrom {
			if excludeFrom.Type == accapi.UserCriteriaTypeEmail {
				excludeEmail = append(excludeEmail, excludeFrom.Domain.Value)
			}
		}

		if len(excludeEmail) > 0 {
			db.Exec(
				tx,
				`
					insert into "grant".exclude_applications_from(project_id, email_suffix) 
					select :project, unnest(cast(:suffix as text[]))
				`,
				db.Params{
					"project": settings.ProjectId,
					"suffix":  excludeEmail,
				},
			)
		}
	})
}

func grantsInitIndex(b *grantIndexBucket, recipient string) {
	if grantGlobals.Testing.Enabled {
		return
	}

	b.Mu.RLock()
	_, ok := b.ApplicationsByEntity[recipient]
	if !ok {
		b.Mu.RUnlock()
		grantsLoadIndex(b, recipient)

		b.Mu.RLock()
	}

	b.Mu.RUnlock()
}

func grantsLoadIndex(b *grantIndexBucket, recipient string) {
	if grantGlobals.Testing.Enabled {
		return
	}

	index := db.NewTx(func(tx *db.Transaction) []accGrantId {
		rows := db.Select[struct{ Id int64 }](
			tx,
			`
				select distinct app.id
				from
					"grant".applications app
					join "grant".requested_resources rr on app.id = rr.application_id
				where
					app.requested_by = :recipient
					or rr.grant_giver = :recipient
				order by app.id -- required for index bucket
		    `,
			db.Params{
				"recipient": recipient,
			},
		)

		var result []accGrantId
		for _, row := range rows {
			result = append(result, accGrantId(row.Id))
		}

		return result
	})

	b.Mu.Lock()
	if _, isLoaded := b.ApplicationsByEntity[recipient]; !isLoaded {
		b.ApplicationsByEntity[recipient] = index
	}
	b.Mu.Unlock()
}

func lGrantsPersist(app *grantApplication) {
	if grantGlobals.Testing.Enabled {
		return
	} else {
		appl := app.Application

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into "grant".applications(id, overall_state, requested_by, created_at, updated_at, synchronized) 
					values (:id, :state, :requested_by, now(), now(), false)
					on conflict (id) do update set
						overall_state = excluded.overall_state,
						requested_by = excluded.requested_by,
						updated_at = excluded.updated_at
			    `,
				db.Params{
					"id":           app.lId(),
					"state":        appl.Status.OverallState,
					"requested_by": appl.CreatedBy,
				},
			)

			var commentIds []int64
			var comments []string
			var commentPostedBy []string

			for _, comment := range appl.Status.Comments {
				commentId, _ := strconv.ParseInt(comment.Id.Value, 10, 64)
				comments = append(comments, comment.Comment)
				commentIds = append(commentIds, commentId)
				commentPostedBy = append(commentPostedBy, comment.Username)
			}

			if len(commentIds) > 0 {
				db.Exec(
					tx,
					`
						insert into "grant".comments(id, application_id, comment, posted_by, created_at) 
						select unnest(cast(:comment_ids as int8[])), :app_id, unnest(cast(:comments as text[])), unnest(cast(:posted_by as text[])), now()
						on conflict do nothing
					`,
					db.Params{
						"app_id":      app.lId(),
						"comment_ids": commentIds,
						"comments":    comments,
						"posted_by":   commentPostedBy,
					},
				)
			}

			db.Exec(
				tx,
				`
					delete from "grant".comments c
					where
						c.application_id = :app_id
						  and not (c.id = some(cast(:comment_ids as int8[])))
			    `,
				db.Params{
					"app_id":      app.lId(),
					"comment_ids": commentIds,
				},
			)

			var revisionIds []int64
			var revisionUpdatedBy []string
			var revisionComments []string
			var revisionStart []int64
			var revisionEnd []int64

			for _, rev := range appl.Status.Revisions {
				revisionIds = append(revisionIds, int64(rev.RevisionNumber))
				revisionUpdatedBy = append(revisionUpdatedBy, rev.UpdatedBy)
				revisionComments = append(revisionComments, rev.Document.RevisionComment.GetOrDefault(""))

				period := rev.Document.AllocationPeriod.Value
				revisionStart = append(revisionStart, period.Start.Value.UnixMilli())
				revisionEnd = append(revisionEnd, period.End.Value.UnixMilli())
			}

			db.Exec(
				tx,
				`
					insert into "grant".revisions(application_id, created_at, updated_by, revision_comment, 
						revision_number, grant_start, grant_end) 
					select :app_id, now(), unnest(cast(:revision_updated_by as text[])), 
						unnest(cast(:revision_comments as text[])), 
						unnest(cast(:revision_ids as int8[])), to_timestamp(unnest(cast(:revision_start as int8[])) / 1000.0), 
						to_timestamp(unnest(cast(:revision_end as int8[])) / 1000.0)
					on conflict do nothing
			    `,
				db.Params{
					"app_id":              app.lId(),
					"revision_updated_by": revisionUpdatedBy,
					"revision_comments":   revisionComments,
					"revision_ids":        revisionIds,
					"revision_start":      revisionStart,
					"revision_end":        revisionEnd,
				},
			)

			var resourceRevId []int
			var resourceCatName []string
			var resourceCatProvider []string
			var resourceGrantGiver []string
			var resourceStart []int64
			var resourceEnd []int64
			var resourceQuota []int64

			for _, rev := range appl.Status.Revisions {
				start := rev.Document.AllocationPeriod.Value.Start.Value.UnixMilli() // TODO This is not good enough
				end := rev.Document.AllocationPeriod.Value.End.Value.UnixMilli()

				for _, resource := range rev.Document.AllocationRequests {
					resourceRevId = append(resourceRevId, rev.RevisionNumber)
					resourceCatName = append(resourceCatName, resource.Category)
					resourceCatProvider = append(resourceCatProvider, resource.Provider)
					resourceGrantGiver = append(resourceGrantGiver, resource.GrantGiver)
					resourceStart = append(resourceStart, start)
					resourceEnd = append(resourceEnd, end)
					resourceQuota = append(resourceQuota, resource.BalanceRequested.GetOrDefault(0))
				}
			}

			if len(resourceRevId) > 0 {
				db.Exec(
					tx,
					`
						with data as (
							select
								unnest(cast(:rev_ids as int8[])) as rev,
								unnest(cast(:cat_names as text[])) as cat_name,
								unnest(cast(:cat_providers as text[])) as cat_provider,
								unnest(cast(:grant_givers as text[])) as grant_giver,
								to_timestamp(unnest(cast(:resource_start as int8[])) / 1000.0) as resource_start,
								to_timestamp(unnest(cast(:resource_end as int8[])) / 1000.0) as resource_end,
								unnest(cast(:resource_quota as int8[])) as quota
						)
						insert into "grant".requested_resources
							(application_id, credits_requested, quota_requested_bytes, product_category,
							start_date, end_date, grant_giver, revision_number)
						select
							:app_id,
							d.quota,
							null,
							pc.id,
							d.resource_start,
							d.resource_end,
							d.grant_giver,
							d.rev
						from
							data d
							join accounting.product_categories pc on 
								d.cat_name = pc.category
								and d.cat_provider = pc.provider
							left join "grant".requested_resources rr on
								rr.application_id = :app_id
								and rr.revision_number = d.rev
								and pc.id = rr.product_category
						where
							rr.revision_number is null
					`,
					db.Params{
						"app_id":         app.lId(),
						"rev_ids":        resourceRevId,
						"cat_names":      resourceCatName,
						"cat_providers":  resourceCatProvider,
						"grant_givers":   resourceGrantGiver,
						"resource_start": resourceStart,
						"resource_end":   resourceEnd,
						"resource_quota": resourceQuota,
					},
				)
			}

			var formsRev []int
			var formsRecipient []string
			var formsRecipientType []string
			var form []string
			var formsReferences []string // json array

			for _, rev := range appl.Status.Revisions {
				formsRev = append(formsRev, rev.RevisionNumber)
				formsRecipient = append(formsRecipient, rev.Document.Recipient.Reference().Value)
				sqlRecipientType := ""
				switch rev.Document.Recipient.Type {
				case accapi.RecipientTypeExistingProject:
					sqlRecipientType = "existing_project"
				case accapi.RecipientTypeNewProject:
					sqlRecipientType = "new_project"
				case accapi.RecipientTypePersonalWorkspace:
					sqlRecipientType = "personal"
				}
				formsRecipientType = append(formsRecipientType, sqlRecipientType)
				form = append(form, rev.Document.Form.Text)

				jsonArr, _ := json.Marshal(rev.Document.ReferenceIds.GetOrDefault([]string{}))
				formsReferences = append(formsReferences, string(jsonArr))
			}

			db.Exec(
				tx,
				`
					with
						data as (
							select
								unnest(cast(:revs as int8[])) as rev,
								unnest(cast(:recipients as text[])) as recipient,
								unnest(cast(:recipient_types as text[])) as recipient_type,
								unnest(cast(:form as text[])) as form,
								unnest(cast(:refs as text[])) as refs
						),
						refs_unwrapped as (
							select d.rev, jsonb_array_elements_text(cast(d.refs as jsonb)) ref
							from data d
						),
						refs as (
							select rev, array_agg(ref) refs
							from refs_unwrapped
							group by rev
						)
					insert into "grant".forms(application_id, revision_number, parent_project_id, recipient, 
						recipient_type, form, reference_ids) 
					select
						:app_id,
						d.rev,
						null,
						d.recipient,
						d.recipient_type,
						d.form,
						coalesce(r.refs, cast(array[] as text[]))
					from
						data d
						left join refs r on d.rev = r.rev
						left join "grant".forms ff on application_id = :app_id and ff.revision_number = d.rev
					where
						ff.application_id is null
					on conflict do nothing
			    `,
				db.Params{
					"app_id":          app.lId(),
					"revs":            formsRev,
					"recipients":      formsRecipient,
					"recipient_types": formsRecipientType,
					"form":            form,
					"refs":            formsReferences,
				},
			)

			var approvalStates []string
			var approvalGivers []string
			for _, a := range appl.Status.StateBreakdown {
				approvalStates = append(approvalStates, string(a.State))
				approvalGivers = append(approvalGivers, a.ProjectId)
			}

			db.Exec(
				tx,
				`
					with data as (
						select
							unnest(cast(:states as text[])) state,
							unnest(cast(:grant_givers as text[])) grant_giver
					)
					insert into "grant".grant_giver_approvals(application_id, project_id, project_title,
						state, updated_by, last_update) 
					select :app_id, d.grant_giver, p.title, d.state, '_ucloud', now()
					from
						data d
						join project.projects p on d.grant_giver = p.id
					on conflict (application_id, project_id) do update set
						state = excluded.state,
						project_title = excluded.project_title,
						updated_by = excluded.updated_by,
						last_update = excluded.last_update
			    `,
				db.Params{
					"app_id":       app.lId(),
					"states":       approvalStates,
					"grant_givers": approvalGivers,
				},
			)
		})
	}
}
