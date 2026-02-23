package accounting

import (
	"fmt"
	"strings"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
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
				catKey := fmt.Sprintf("%s/%s", request.Category, request.Provider)
				resources[catKey] = int(request.BalanceRequested.GetOrDefault(0))
			}

			title := doc.Recipient.Reference().Value
			titleIsProjectId := doc.Recipient.Type == accapi.RecipientTypeExistingProject
			if titleIsProjectId {
				title = grantsExportProjectIdToTitle(title)
			}

			result = append(result, accapi.GrantsExportResponse{
				Id:               item.Id.Value,
				Title:            title,
				SubmittedBy:      item.CreatedBy,
				OptionalUserInfo: item.Status.OptionalUserInfo,
				SubmittedAt:      item.CreatedAt,
				StartDate:        startTime,
				DurationMonths:   int(allocationDuration.Hours() / 24.0 / 30.0),
				State:            item.Status.OverallState,
				GrantGiver:       grantsExportProjectIdToTitle(string(actor.Project.Value)),
				LastUpdatedAt:    item.UpdatedAt,
				Resources:        resources,
			})
		}

		next = p.Next
		if !next.Present {
			break
		}
	}

	return result
}

func GrantsExportBrowseToCsv(lines []accapi.GrantsExportResponse) string {
	s := &strings.Builder{}

	categories := map[string]util.Empty{}
	for _, line := range lines {
		for resource := range line.Resources {
			categories[resource] = util.Empty{}
		}
	}

	var categoriesArray []string
	for item := range categories {
		categoriesArray = append(categoriesArray, item)
	}
	s.WriteString("id,title,submitted_by,submitted_at,start,duration_months,state,grant_giver,last_updated_at,")
	s.WriteString("organization_full_name,department,research_field,position,gender,unit")

	for _, item := range categoriesArray {
		catAndProvider := strings.SplitN(item, "/", 2)
		category := catAndProvider[0]
		provider := ""
		if len(catAndProvider) == 2 {
			provider = catAndProvider[1]
		}

		unitName := &strings.Builder{}

		productCategory, err := ProductCategoryRetrieve(rpc.ActorSystem, category, provider)
		headline := item
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

			headline += " (" + unitName.String() + ")"
		}

		s.WriteRune(',')
		grantsWriteQuotedString(s, headline)
	}

	s.WriteRune('\n')

	for _, item := range lines {
		t := item.SubmittedAt

		grantsWriteQuotedString(s, item.Id)
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.Title)
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.SubmittedBy)
		s.WriteRune(',')
		grantsWriteQuotedString(s, t.Time().Format(time.RFC3339))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.StartDate.Time().Format(time.RFC3339))
		s.WriteRune(',')
		grantsWriteQuotedString(s, fmt.Sprint(item.DurationMonths))
		s.WriteRune(',')
		grantsWriteQuotedString(s, fmt.Sprint(item.State))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.GrantGiver)
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.LastUpdatedAt.Time().Format(time.RFC3339))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.OrganizationFullName.GetOrDefault("Unknown"))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.Department.GetOrDefault("Unknown"))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.ResearchField.GetOrDefault("Unknown"))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.Position.GetOrDefault("Unknown"))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.Gender.GetOrDefault("Unknown"))
		s.WriteRune(',')
		grantsWriteQuotedString(s, item.OptionalUserInfo.Unit.GetOrDefault("Unknown"))
		for _, category := range categoriesArray {
			s.WriteRune(',')
			grantsWriteQuotedString(s, fmt.Sprint(item.Resources[category]))
		}
		s.WriteRune('\n')
	}

	return s.String()
}

func grantsWriteQuotedString(b *strings.Builder, value string) {
	if !strings.ContainsAny(value, ",\"\n\r") {
		b.WriteString(value)
		return
	}

	b.WriteRune('"')
	for _, r := range value {
		if r == '"' {
			b.WriteString("\"\"")
		} else {
			b.WriteRune(r)
		}
	}
	b.WriteRune('"')
}
