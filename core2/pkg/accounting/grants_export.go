package accounting

import (
	"fmt"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initGrantsExport() {
	accapi.GrantsExport.Handler(func(info rpc.RequestInfo, request util.Empty) ([]accapi.GrantsExportResponse, *util.HttpError) {
		return GrantsExportBrowse(info.Actor), nil
	})

	accapi.GrantsExportCsv.Handler(func(info rpc.RequestInfo, request util.Empty) (accapi.GrantsExportCsvResponse, *util.HttpError) {
		csv := GrantsExportBrowseToCsv(GrantsExportBrowse(info.Actor))
		return accapi.GrantsExportCsvResponse{
			FileName: "grants.csv",
			CsvData:  csv,
		}, nil
	})
}

func GrantsExportBrowse(actor rpc.Actor) []accapi.GrantsExportResponse {
	var result []accapi.GrantsExportResponse

	var next util.Option[string]
	for {
		p := GrantsBrowse(
			actor,
			accapi.GrantsBrowseRequest{
				ItemsPerPage:               250,
				Next:                       next,
				IncludeIngoingApplications: util.OptValue(true),
				Filter:                     util.OptValue(accapi.GrantApplicationFilterShowAll),
			},
		)

		for _, item := range p.Items {
			doc := item.CurrentRevision.Document
			startTime := doc.AllocationPeriod.Value.Start.Value
			endTime := doc.AllocationPeriod.Value.End.Value
			allocationDuration := endTime.Time().Sub(startTime.Time())

			result = append(result, accapi.GrantsExportResponse{
				Id:             item.Id.Value,
				Title:          doc.Recipient.Reference().Value,
				SubmittedBy:    item.CreatedBy,
				SubmittedAt:    item.CreatedAt,
				StartDate:      startTime,
				DurationMonths: int(allocationDuration.Hours() / 24.0 / 30.0),
				State:          item.Status.OverallState,
				GrantGiver:     string(actor.Project.Value),
				LastUpdatedAt:  item.UpdatedAt,
				Resources:      nil, // TODO add resources
			})
		}

		next = p.Next
		if !next.Present {
			break
		}
	}

	return result
}

// TODO convert JSON to CSV

func GrantsExportBrowseToCsv(lines []accapi.GrantsExportResponse) string {
	s := &strings.Builder{}

	s.WriteString("id,title,submitted_by,submitted_at,start,duration_months,state,grant_giver, resources, approved_or_rejected_by,approved_or_rejected_at\n")

	for _, item := range lines {
		t := item.SubmittedAt

		s.WriteString(item.Id)
		s.WriteRune(',')
		s.WriteString(item.Title)
		s.WriteRune(',')
		s.WriteString(item.SubmittedBy)
		s.WriteRune(',')
		s.WriteString(t.Time().Format(time.RFC3339))
		s.WriteRune(',')
		s.WriteString(item.StartDate.Time().Format(time.RFC3339))
		s.WriteRune(',')
		s.WriteString(fmt.Sprint(item.DurationMonths))
		s.WriteRune(',')
		s.WriteString(fmt.Sprint(item.State))
		s.WriteRune(',')
		s.WriteString(item.GrantGiver)
		s.WriteRune(',')
		s.WriteString(fmt.Sprint(item.LastUpdatedAt))
		s.WriteRune(',')
		s.WriteString(fmt.Sprint(item.Resources))
		// TODO add approved_or_rejected_by and approved_or_rejected_at
		s.WriteRune('\n')
	}

	return s.String()
}
