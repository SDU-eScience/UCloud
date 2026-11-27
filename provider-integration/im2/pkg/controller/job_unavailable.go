package controller

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"slices"
	"strings"
	"time"

	cfg "ucloud.dk/pkg/config"
	gw "ucloud.dk/pkg/gateway"
	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
)

const jobFinishedSessionRetention = 24 * time.Hour

type WebSessionDiagnostics struct {
	AppPort       int
	PodPhase      string
	PodStateLabel string
	PodDetail     string
	RestartCount  int32
	ProbeStatus   string
	Logs          string
	LogsTruncated bool
	LogsAvailable bool
}

type jobFinishedSession struct {
	JobId      string
	Rank       int
	Suffix     string
	Address    string
	AuthToken  string
	FinishedAt time.Time
}

var finishedWebSessionsByHost = map[string][]jobFinishedSession{}

func jobStoreFinishedSessionLocked(item jobFinishedSession) {
	if _, _, _, isJobHost := cfg.WebParseJobHostname(item.Address); !isJobHost {
		return
	}
	key := strings.ToLower(item.Address)
	finishedWebSessionsByHost[key] = append(finishedWebSessionsByHost[key], item)
}

func jobRecordFinishedSessionsLocked(session *webSession, finishedAt time.Time) {
	if session == nil {
		return
	}
	for _, ing := range session.IngressBySuffix {
		isWeb := (ing.Flags & RegisteredIngressFlagsWeb) != 0
		if !isWeb || !ing.AuthToken.Present {
			continue
		}
		jobStoreFinishedSessionLocked(jobFinishedSession{
			JobId:      ing.JobId,
			Rank:       ing.Rank,
			Suffix:     ing.Suffix,
			Address:    ing.Address,
			AuthToken:  ing.AuthToken.Value,
			FinishedAt: finishedAt,
		})
	}
}

func jobPruneFinishedSessionsLocked(cutoff time.Time) {
	for host, items := range finishedWebSessionsByHost {
		items = slices.DeleteFunc(items, func(item jobFinishedSession) bool {
			return item.FinishedAt.Before(cutoff)
		})
		if len(items) == 0 {
			delete(finishedWebSessionsByHost, host)
		} else {
			finishedWebSessionsByHost[host] = items
		}
	}
}

func jobPurgeFinishedSessionsInMemory(jobId string) {
	webSessionsMutex.Lock()
	defer webSessionsMutex.Unlock()

	for host, items := range finishedWebSessionsByHost {
		items = slices.DeleteFunc(items, func(item jobFinishedSession) bool {
			return item.JobId == jobId
		})
		if len(items) == 0 {
			delete(finishedWebSessionsByHost, host)
		} else {
			finishedWebSessionsByHost[host] = items
		}
	}
}

func jobPruneExpiredWebSessions() {
	cutoff := time.Now().Add(-jobFinishedSessionRetention)

	webSessionsMutex.Lock()
	jobPruneFinishedSessionsLocked(cutoff)
	webSessionsMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from web_sessions
				where
					finished_at is not null
					and finished_at < :cutoff
			`,
			db.Params{
				"cutoff": cutoff,
			},
		)
	})
}

func jobRequestHost(r *http.Request) string {
	host := strings.TrimSpace(r.Host)
	if host == "" {
		return ""
	}
	parsedHost, _, err := net.SplitHostPort(host)
	if err == nil {
		return strings.ToLower(parsedHost)
	}
	return strings.ToLower(host)
}

func jobUcloudUrl() string {
	if cfg.Provider == nil {
		return ""
	}
	return cfg.Provider.Hosts.UCloudPublic.ToURL()
}

func jobUcloudJobUrl(jobId string) string {
	baseUrl := jobUcloudUrl()
	if baseUrl == "" || jobId == "" {
		return baseUrl
	}
	return strings.TrimRight(baseUrl, "/") + "/app/jobs/properties/" + jobId
}

func jobExtractSessionTokens(r *http.Request) []string {
	var tokens []string
	for _, cookie := range r.Cookies() {
		if strings.HasPrefix(cookie.Name, "ucloud-compute-session-") && cookie.Value != "" {
			tokens = append(tokens, cookie.Value)
		}
	}
	return tokens
}

func jobTokenMatches(expected string, candidates []string) bool {
	if expected == "" || len(candidates) == 0 {
		return false
	}
	expectedBytes := []byte(expected)
	for _, candidate := range candidates {
		if subtle.ConstantTimeCompare(expectedBytes, []byte(candidate)) == 1 {
			return true
		}
	}
	return false
}

func jobFindActiveWebIngressByHost(host string) (webSessionIngress, bool) {
	webSessionsMutex.RLock()
	defer webSessionsMutex.RUnlock()

	for _, session := range webSessions {
		for _, ing := range session.IngressBySuffix {
			isWeb := (ing.Flags & RegisteredIngressFlagsWeb) != 0
			if isWeb && strings.EqualFold(ing.Address, host) {
				return ing, true
			}
		}
	}
	return webSessionIngress{}, false
}

func jobFindFinishedSession(host string, tokens []string) (jobFinishedSession, bool) {
	if len(tokens) == 0 {
		return jobFinishedSession{}, false
	}
	if _, _, _, isJobHost := cfg.WebParseJobHostname(host); !isJobHost {
		return jobFinishedSession{}, false
	}

	cutoff := time.Now().Add(-jobFinishedSessionRetention)
	key := strings.ToLower(host)

	webSessionsMutex.RLock()
	defer webSessionsMutex.RUnlock()

	items := finishedWebSessionsByHost[key]
	for i := len(items) - 1; i >= 0; i-- {
		item := items[i]
		if item.FinishedAt.Before(cutoff) {
			continue
		}
		if jobTokenMatches(item.AuthToken, tokens) {
			return item, true
		}
	}
	return jobFinishedSession{}, false
}

func jobDisplayTitle(job *orcapi.Job) string {
	if job == nil {
		return ""
	}
	if job.Specification.Name != "" {
		return job.Specification.Name
	}
	if job.Status.ResolvedApplication.Present && job.Status.ResolvedApplication.Value.Metadata.Title != "" {
		return job.Status.ResolvedApplication.Value.Metadata.Title
	}
	return job.Specification.Application.Name
}

func jobFinishedTimestamp(job *orcapi.Job, fallback time.Time) time.Time {
	if job != nil {
		for i := len(job.Updates) - 1; i >= 0; i-- {
			update := job.Updates[i]
			if update.State.Present && update.State.Value.IsFinal() && update.Timestamp.UnixMilli() > 0 {
				return update.Timestamp.Time()
			}
		}
	}
	return fallback
}

func jobRequestWantsHtml(r *http.Request) bool {
	accept := strings.ToLower(r.Header.Get("Accept"))
	return strings.Contains(accept, "text/html")
}

func jobWriteJson(writer http.ResponseWriter, statusCode int, payload any) {
	encoded, err := json.Marshal(payload)
	if err != nil {
		writer.WriteHeader(http.StatusInternalServerError)
		return
	}
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.Header().Set("Cache-Control", "no-store")
	writer.WriteHeader(statusCode)
	_, _ = writer.Write(encoded)
}

func jobBuildFinishedStatusResponse(job *orcapi.Job, finishedAt time.Time) gw.SessionStatusResponse {
	endedAt := jobFinishedTimestamp(job, finishedAt)
	finishedIso := ""
	if !endedAt.IsZero() {
		finishedIso = endedAt.UTC().Format(time.RFC3339)
	}

	return gw.SessionStatusResponse{
		State:        "finished",
		JobId:        job.Id,
		JobTitle:     jobDisplayTitle(job),
		JobState:     string(job.Status.State),
		FinishedAt:   finishedIso,
		UcloudUrl:    jobUcloudUrl(),
		UcloudJobUrl: jobUcloudJobUrl(job.Id),
	}
}

func jobBuildUnauthenticatedStatusResponse(host string) gw.SessionStatusResponse {
	ucloudUrl := jobUcloudUrl()
	ucloudJobUrl := ""
	parsedJobId, _, _, isJobHost := cfg.WebParseJobHostname(host)
	if isJobHost {
		ucloudJobUrl = jobUcloudJobUrl(parsedJobId)
	}

	return gw.SessionStatusResponse{
		State:        "unauthenticated",
		UcloudUrl:    ucloudUrl,
		UcloudJobUrl: ucloudJobUrl,
	}
}

func jobHandleSessionStatus(writer http.ResponseWriter, request *http.Request) {
	if !checkEnvoySecret(writer, request) {
		return
	}

	host := jobRequestHost(request)
	tokens := jobExtractSessionTokens(request)

	if ing, ok := jobFindActiveWebIngressByHost(host); ok {
		if !ing.AuthToken.Present {
			jobWriteJson(writer, http.StatusOK, gw.SessionStatusResponse{
				State:     "service_unavailable",
				UcloudUrl: jobUcloudUrl(),
			})
			return
		}

		if !jobTokenMatches(ing.AuthToken.Value, tokens) {
			jobWriteJson(writer, http.StatusOK, jobBuildUnauthenticatedStatusResponse(host))
			return
		}

		job, jobOk := jobRetrieveLocal(ing.JobId)
		if !jobOk {
			jobWriteJson(writer, http.StatusOK, jobBuildUnauthenticatedStatusResponse(host))
			return
		}

		if job.Status.State.IsFinal() {
			jobWriteJson(writer, http.StatusOK, jobBuildFinishedStatusResponse(job, time.Time{}))
			return
		}

		diagnostics := WebSessionDiagnostics{}
		if Jobs.InspectWebSession != nil {
			diagnostics = Jobs.InspectWebSession(job, ing.Rank, ing.Suffix, ing.Target, ing.TLS)
		}

		targetPort := ing.Target.Port
		if diagnostics.AppPort > 0 {
			targetPort = diagnostics.AppPort
		}

		startedAt := ""
		if job.Status.StartedAt.Present {
			startedAt = job.Status.StartedAt.Value.Time().UTC().Format(time.RFC3339)
		}

		targetName := strings.TrimPrefix(ing.Suffix, "-")

		jobWriteJson(writer, http.StatusOK, gw.SessionStatusResponse{
			State:         "running_unavailable",
			JobId:         job.Id,
			JobTitle:      jobDisplayTitle(job),
			Rank:          ing.Rank,
			TargetName:    targetName,
			TargetPort:    targetPort,
			TargetTls:     ing.TLS,
			JobState:      string(job.Status.State),
			StartedAt:     startedAt,
			UcloudUrl:     jobUcloudUrl(),
			UcloudJobUrl:  jobUcloudJobUrl(job.Id),
			PodPhase:      diagnostics.PodPhase,
			PodStateLabel: diagnostics.PodStateLabel,
			PodDetail:     diagnostics.PodDetail,
			RestartCount:  diagnostics.RestartCount,
			ProbeStatus:   diagnostics.ProbeStatus,
			Logs:          diagnostics.Logs,
			LogsTruncated: diagnostics.LogsTruncated,
			LogsAvailable: diagnostics.LogsAvailable,
		})
		return
	}

	if finished, ok := jobFindFinishedSession(host, tokens); ok {
		job, jobOk := jobRetrieveLocal(finished.JobId)
		if jobOk && job.Status.State.IsFinal() {
			jobWriteJson(writer, http.StatusOK, jobBuildFinishedStatusResponse(job, finished.FinishedAt))
			return
		}
	}

	if _, _, _, isJobHost := cfg.WebParseJobHostname(host); isJobHost {
		jobWriteJson(writer, http.StatusOK, jobBuildUnauthenticatedStatusResponse(host))
		return
	}

	jobWriteJson(writer, http.StatusOK, gw.SessionStatusResponse{
		State:     "service_unavailable",
		UcloudUrl: jobUcloudUrl(),
	})
}

func jobFormatTimestampHuman(ts time.Time) string {
	if ts.IsZero() {
		return ""
	}
	elapsed := time.Since(ts)
	if elapsed < 0 {
		return ts.UTC().Format("2006-01-02 15:04 UTC")
	}
	if elapsed < time.Minute {
		return fmt.Sprintf("%ds ago", int(elapsed.Seconds()))
	}
	if elapsed < time.Hour {
		return fmt.Sprintf("%dm ago", int(elapsed.Minutes()))
	}
	if elapsed < 24*time.Hour {
		return fmt.Sprintf("%dh ago", int(elapsed.Hours()))
	}
	return ts.UTC().Format("2006-01-02 15:04 UTC")
}

func jobRenderFinishedPage(writer http.ResponseWriter, request *http.Request, job *orcapi.Job, finishedAt time.Time) {
	if !jobRequestWantsHtml(request) {
		jobWriteJson(writer, http.StatusGone, jobBuildFinishedStatusResponse(job, finishedAt))
		return
	}

	stateLabel := "Finished"
	heading := "This job has finished"

	switch job.Status.State {
	case orcapi.JobStateExpired:
		stateLabel = "Expired"
		heading = "This job has expired"
	case orcapi.JobStateFailure:
		stateLabel = "Failed"
		heading = "This job has failed"
	case orcapi.JobStateSuccess:
		stateLabel = "Completed"
		heading = "This job has completed"
	}

	metaItems := []gw.UnavailablePageMetaItem{}
	title := jobDisplayTitle(job)
	if title != "" {
		metaItems = append(metaItems, gw.UnavailablePageMetaItem{
			Label: "Application",
			Value: title,
		})
	}
	metaItems = append(metaItems, gw.UnavailablePageMetaItem{
		Label: "Job ID",
		Value: "#" + job.Id,
		Mono:  true,
	})
	metaItems = append(metaItems, gw.UnavailablePageMetaItem{
		Label: "Final state",
		Value: stateLabel,
	})

	endedAt := jobFinishedTimestamp(job, finishedAt)
	if !endedAt.IsZero() {
		metaItems = append(metaItems, gw.UnavailablePageMetaItem{
			Label: "Ended",
			Value: jobFormatTimestampHuman(endedAt),
		})
	}

	html := gw.RenderUnavailablePage(gw.UnavailablePageModel{
		PageTitle: heading,
		BrandHref: jobUcloudUrl(),
		Heading:   heading,
		MetaItems: metaItems,
		TipsTitle: "What you can do next",
		Tips: []string{
			"Open the job in UCloud to inspect its output folder, logs, and run summary.",
			"You can restart or duplicate the job with the same parameters from the job page in UCloud.",
		},
		PrimaryActionHref:  jobUcloudJobUrl(job.Id),
		PrimaryActionLabel: "Open job in UCloud",
		ShowRetry:          false,
		EnableProbe:        false,
	})

	writer.Header().Set("Content-Type", "text/html; charset=utf-8")
	writer.Header().Set("Cache-Control", "no-store")
	writer.WriteHeader(http.StatusGone)
	_, _ = writer.Write([]byte(html))
}

func jobRenderUnauthenticatedPage(writer http.ResponseWriter, request *http.Request, host string) {
	if !jobRequestWantsHtml(request) {
		jobWriteJson(writer, http.StatusUnauthorized, jobBuildUnauthenticatedStatusResponse(host))
		return
	}

	ucloudUrl := jobUcloudUrl()
	primaryHref := ucloudUrl
	primaryLabel := "Open UCloud"

	parsedJobId, _, _, isJobHost := cfg.WebParseJobHostname(host)
	if isJobHost {
		primaryHref = jobUcloudJobUrl(parsedJobId)
		primaryLabel = "Open job in UCloud"
	}

	html := gw.RenderUnavailablePage(gw.UnavailablePageModel{
		PageTitle:    "Authentication required",
		BrandHref:    ucloudUrl,
		Heading:      "Session authentication required",
		CalloutClass: "info",
		CalloutBody:  "This browser does not have an active authenticated session for this job, or the job is no longer available.",
		TipsTitle:    "To continue",
		Tips: []string{
			"Open the job on UCloud and click \"Open interface\".",
			"If you were previously viewing this job, it may have finished or your session may have expired.",
		},
		PrimaryActionHref:  primaryHref,
		PrimaryActionLabel: primaryLabel,
		ShowRetry:          false,
		EnableProbe:        false,
	})

	writer.Header().Set("Content-Type", "text/html; charset=utf-8")
	writer.Header().Set("Cache-Control", "no-store")
	writer.WriteHeader(http.StatusUnauthorized)
	_, _ = writer.Write([]byte(html))
}

func jobRenderProbingFallbackPage(writer http.ResponseWriter, request *http.Request) {
	if !jobRequestWantsHtml(request) {
		jobWriteJson(writer, http.StatusServiceUnavailable, gw.SessionStatusResponse{
			State:     "running_unavailable",
			UcloudUrl: jobUcloudUrl(),
		})
		return
	}

	writer.Header().Set("Content-Type", "text/html; charset=utf-8")
	writer.Header().Set("Cache-Control", "no-store")
	writer.WriteHeader(http.StatusServiceUnavailable)
	_, _ = writer.Write([]byte(gw.RenderBadGatewayHtml()))
}

func jobHandleSessionUnavailable(writer http.ResponseWriter, request *http.Request) {
	if !checkEnvoySecret(writer, request) {
		return
	}

	host := jobRequestHost(request)
	tokens := jobExtractSessionTokens(request)

	if ing, ok := jobFindActiveWebIngressByHost(host); ok {
		if ing.AuthToken.Present && jobTokenMatches(ing.AuthToken.Value, tokens) {
			job, jobOk := jobRetrieveLocal(ing.JobId)
			if jobOk {
				if job.Status.State.IsFinal() {
					jobRenderFinishedPage(writer, request, job, time.Time{})
					return
				}
				jobRenderProbingFallbackPage(writer, request)
				return
			}
		}
	}

	if finished, ok := jobFindFinishedSession(host, tokens); ok {
		job, jobOk := jobRetrieveLocal(finished.JobId)
		if jobOk && job.Status.State.IsFinal() {
			jobRenderFinishedPage(writer, request, job, finished.FinishedAt)
			return
		}
	}

	jobRenderUnauthenticatedPage(writer, request, host)
}

func jobServeAuthorizeAppNotFound(writer http.ResponseWriter, request *http.Request, token string) {
	host := jobRequestHost(request)
	if token != "" {
		if finished, ok := jobFindFinishedSession(host, []string{token}); ok {
			job, jobOk := jobRetrieveLocal(finished.JobId)
			if jobOk && job.Status.State.IsFinal() {
				jobRenderFinishedPage(writer, request, job, finished.FinishedAt)
				return
			}
		}
	}
	jobRenderUnauthenticatedPage(writer, request, host)
}
