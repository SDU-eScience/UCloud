package accounting

import (
	"fmt"
	"strings"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
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

func grantsExportProjectIdToTitle(projectId string) string {
	return db.NewTx(func(tx *db.Transaction) string {
		id, ok := coreutil.ProjectRetrieveFromDatabase(tx, projectId)
		if ok {
			return id.Specification.Title
		}
		return projectId
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

			resources := map[string]int{}
			for _, request := range doc.AllocationRequests {
				resources[request.Category] = int(request.BalanceRequested.GetOrDefault(0))
			}

			title := doc.Recipient.Reference().Value
			titleIsProjectId := doc.Recipient.Type == accapi.RecipientTypeExistingProject
			if titleIsProjectId {
				title = grantsExportProjectIdToTitle(title)
			}

			result = append(result, accapi.GrantsExportResponse{
				Id:             item.Id.Value,
				Title:          title,
				SubmittedBy:    item.CreatedBy,
				SubmittedAt:    item.CreatedAt,
				StartDate:      startTime,
				DurationMonths: int(allocationDuration.Hours() / 24.0 / 30.0),
				State:          item.Status.OverallState,
				GrantGiver:     grantsExportProjectIdToTitle(string(actor.Project.Value)),
				LastUpdatedAt:  item.UpdatedAt,
				Resources:      resources,
			})
		}

		next = p.Next
		if !next.Present {
			break
		}
	}

	return result
}

// Convert JSON to CSV

func GrantsExportBrowseToCsv(lines []accapi.GrantsExportResponse) string {
	s := &strings.Builder{}

	categories := map[string]util.Empty{}
	for _, line := range lines {
		for resource, _ := range line.Resources {
			categories[resource] = util.Empty{}
		}
	}

	categoriesArray := []string{}
	for item := range categories {
		categoriesArray = append(categoriesArray, item)
	}

	s.WriteString("id,title,submitted_by,submitted_at,start,duration_months,state,grant_giver,last_updated_at")

	for _, item := range categoriesArray {
		s.WriteString("," + item)
		// Write ("$UNIT"). For example: (GB)
		provider := "gok8s"

		unitName := &strings.Builder{}

		productCategory, err := ProductCategoryRetrieve(rpc.ActorSystem, item, provider)
		if err == nil {
			unitName.WriteString(productCategory.AccountingUnit.Name)

			switch productCategory.AccountingFrequency {
			case accapi.AccountingFrequencyPeriodicMinute:
				unitName.WriteString("-min")
			case accapi.AccountingFrequencyPeriodicHour:
				unitName.WriteString("-hour")
			case accapi.AccountingFrequencyPeriodicDay:
				unitName.WriteString("-day")
			}

			s.WriteString(" (")
			s.WriteString(unitName.String())
			s.WriteString(")")
		}
	}

	s.WriteRune('\n')

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
		s.WriteString(item.LastUpdatedAt.Time().Format(time.RFC3339))
		for _, category := range categoriesArray {
			s.WriteRune(',')
			s.WriteString(fmt.Sprint(item.Resources[category]))
		}
		s.WriteRune('\n')
	}

	return s.String()
}
