package reporting

import (
	"bytes"
	"encoding/gob"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"
	"ucloud.dk/shared/pkg/util"
)

type ServiceProviderReport struct {
	Provider  string
	Start     time.Time
	End       time.Time
	Resources map[string]ResourceUseAndCapacity // by category
}

type ResourceUseAndCapacity struct {
	Usage    float64
	Capacity int64
	Unit     string
}

type UserReport struct {
	Id           string // ucloud username
	Organization string // wayf orgid
	Project      string // ucloud project id
	GrantGiver   string // grant giver user in the report
	Start        time.Time
	End          time.Time
	Resources    map[string]ResourceUseAndCapacity
}

type reportingSample struct {
	Timestamp    int64
	WalletId     int64
	Quota        int64
	LocalUsage   int64
	TreeUsage    int64
	UsageByGroup string
	Project      string
	Category     string
	Provider     string
}

type DeicGrantGiver struct {
	Access         DeicAccessType
	UniversityCode DeicUniversityId
}

type GrantGiver struct {
	Title string
	Deic  DeicGrantGiver
}

type allocation struct {
	Id       int64
	WalletId int64
	Parent   int64
	Start    time.Time
	End      time.Time
	Quota    int64
	Category string
	Provider string
	Project  string
}

type projectMember struct {
	UserId  string
	Project string
	OrgId   string
	JobRuns int64
}

type normalizedProjectMember struct {
	Info     projectMember
	Fraction float64 // 0 to 1
}

type normalizedProject struct {
	Id      string
	Members []normalizedProjectMember
}

func RunReporting(provider string, start, end time.Time, dumpOnly bool, noDump bool, dir string) {
	var rawMembers []projectMember
	var rawAllocations []allocation
	var rawSamples []reportingSample

	if dumpOnly || !noDump {
		fmt.Printf("Dumping project members...\n")
		memberBytes := fetchAndDumpMembers(provider, start, end)
		fmt.Printf("Dumping allocations...\n")
		allocBytes := fetchAndDumpAllocations(provider, start, end)
		fmt.Printf("Dumping wallet samples...\n")
		sampleBytes := fetchAndDumpWallets(provider, start, end)
		fmt.Printf("OK\n")

		if !dumpOnly {
			err := gob.NewDecoder(bytes.NewBuffer(memberBytes)).Decode(&rawMembers)
			if err != nil {
				panic(err)
			}

			err = gob.NewDecoder(bytes.NewBuffer(allocBytes)).Decode(&rawAllocations)
			if err != nil {
				panic(err)
			}

			err = gob.NewDecoder(bytes.NewBuffer(sampleBytes)).Decode(&rawSamples)
			if err != nil {
				panic(err)
			}
		} else {
			err := os.WriteFile(filepath.Join(dir, "members.bin"), memberBytes, 0660)
			if err != nil {
				panic(err)
			}

			err = os.WriteFile(filepath.Join(dir, "allocations.bin"), allocBytes, 0660)
			if err != nil {
				panic(err)
			}

			err = os.WriteFile(filepath.Join(dir, "samples.bin"), sampleBytes, 0660)
			if err != nil {
				panic(err)
			}
		}
	} else {
		memberBytes, err := os.ReadFile(filepath.Join(dir, "members.bin"))
		if err != nil {
			panic(err)
		}

		allocBytes, err := os.ReadFile(filepath.Join(dir, "allocations.bin"))
		if err != nil {
			panic(err)
		}

		sampleBytes, err := os.ReadFile(filepath.Join(dir, "samples.bin"))
		if err != nil {
			panic(err)
		}

		err = gob.NewDecoder(bytes.NewBuffer(memberBytes)).Decode(&rawMembers)
		if err != nil {
			panic(err)
		}

		err = gob.NewDecoder(bytes.NewBuffer(allocBytes)).Decode(&rawAllocations)
		if err != nil {
			panic(err)
		}

		err = gob.NewDecoder(bytes.NewBuffer(sampleBytes)).Decode(&rawSamples)
		if err != nil {
			panic(err)
		}
	}

	if dumpOnly {
		return
	}

	fmt.Printf("Ready to process %v %v %v\n", len(rawMembers), len(rawAllocations), len(rawSamples))

	// Normalize projects
	// -----------------------------------------------------------------------------------------------------------------

	projects := map[string]*normalizedProject{}

	for _, member := range rawMembers {
		p := projects[member.Project]
		if p == nil {
			p = &normalizedProject{}
		}

		p.Id = member.Project

		if member.JobRuns == 0 {
			member.JobRuns = 1 // compensate for usage fraction for storage only users by giving them at least 1 job
		}

		p.Members = append(p.Members, normalizedProjectMember{
			Info:     member,
			Fraction: 0,
		})

		projects[member.Project] = p
	}

	for _, p := range projects {
		sum := int64(0)
		for _, m := range p.Members {
			sum += m.Info.JobRuns
		}

		for i, m := range p.Members {
			m.Fraction = float64(m.Info.JobRuns) / float64(sum)
			p.Members[i] = m
		}
	}

	var reports []reportByGrantGiver

	for grantGiver, info := range grantGivers {
		result := processReport(provider, start, end, rawAllocations, grantGiver, rawSamples, projects)
		reports = append(reports, result)

		fmt.Printf("\n\n%s\n---------------------------\n", info.Title)
		var keys []util.Tuple2[string, string]

		for k, v := range result.CenterPeriod.Resources {
			keys = append(keys, util.Tuple2[string, string]{k, v.Unit})
		}

		unitWeight := func(unit string) int {
			if unit == coreHours {
				return 1
			} else if unit == gpuHours {
				return 2
			} else if unit == storage {
				return 3
			} else {
				return -1
			}
		}

		slices.SortFunc(keys, func(a, b util.Tuple2[string, string]) int {
			aWeight := unitWeight(a.Second)
			bWeight := unitWeight(b.Second)

			if aWeight == -1 || bWeight == -1 {
				cmp := strings.Compare(a.Second, b.Second)
				if cmp != 0 {
					return cmp
				}
			} else if aWeight < bWeight {
				return -1
			} else if aWeight > bWeight {
				return 1
			}

			return strings.Compare(a.First, b.First)
		})

		for _, kv := range keys {
			k := kv.First
			v := result.CenterPeriod.Resources[k]
			fmt.Printf("%v: %.2f %v\n", k, v.Usage, v.Unit)
		}

		jsonData, _ := json.Marshal(result)
		_ = os.WriteFile(
			filepath.Join(
				dir,
				fmt.Sprintf("out-%s.json", strings.ToLower(strings.ReplaceAll(info.Title, " ", "-"))),
			),
			jsonData,
			0660,
		)
	}

	deicReports := convertReports(start, end, reports)
	for _, c := range deicReports.Centers {
		{
			jsonData, _ := json.Marshal(c.Period)
			_ = os.WriteFile(
				filepath.Join(dir, "Center.json"),
				jsonData,
				0660,
			)
		}

		{
			jsonData, _ := json.Marshal(c.Daily)
			_ = os.WriteFile(
				filepath.Join(dir, "CenterDaily.json"),
				jsonData,
				0660,
			)
		}
	}

	{
		jsonData, _ := json.Marshal(deicReports.Persons)
		_ = os.WriteFile(
			filepath.Join(dir, "Persons.json"),
			jsonData,
			0660,
		)
	}
}

type reportByGrantGiver struct {
	GrantGiver   string
	CenterDaily  map[time.Time]*ServiceProviderReport
	CenterPeriod ServiceProviderReport
	PersonDaily  map[personKey]map[time.Time]*UserReport
	PersonPeriod map[personKey]*UserReport
}

type personKey struct {
	Username string
	Project  string
}

func processReport(
	provider string,
	start time.Time,
	end time.Time,
	rawAllocations []allocation,
	rootGrantGiver string,
	rawSamples []reportingSample,
	projects map[string]*normalizedProject,
) reportByGrantGiver {
	// Determine allocation percentages by grant giver
	// -----------------------------------------------------------------------------------------------------------------
	unitTable := map[string]string{
		"u1-standard":   coreHours,
		"u1-standard-h": coreHours,
		"u1-gpu":        gpuHours,
		"u1-gpu-h":      gpuHours,
		"u1-fat-h":      coreHours,
		"u2-gpu-h":      gpuHours,
		"u3-gpu":        gpuHours,
		"u1-cephfs":     storage,
	}

	unitConversionTable := map[string]float64{
		"u1-standard":   1.0 / (1432.0 * 60),
		"u1-standard-h": 1.0 / 60.0,
		"u1-gpu":        1.0 / (175660 * 60),
		"u1-gpu-h":      1 / 60.0,
		"u1-fat-h":      1 / 60.0,
		"u2-gpu-h":      1 / 60.0,
		"u3-gpu":        1 / 60.0,
		"u1-cephfs":     1.0,
	}

	rootGrantGiverWallets := map[string]int64{}
	rawAllocationPercentages := map[int64]float64{}
	walletSumByParent := map[int64]map[int64]int64{}

	for _, alloc := range rawAllocations {
		if alloc.Project == rootGrantGiver {
			rootGrantGiverWallets[alloc.Category] = alloc.WalletId
		}

		current, ok := walletSumByParent[alloc.WalletId]
		if !ok {
			current = map[int64]int64{}
			walletSumByParent[alloc.WalletId] = current
		}

		current[alloc.Parent] = current[alloc.Parent] + alloc.Quota
	}

	// category -> project -> percent from rootGrantGiver
	allocationPercentages := map[string]map[string]float64{}

	totalByCategory := map[string]int64{}
	totalFromGrantGiverByCategory := map[string]int64{}

	didChange := true
	for didChange {
		didChange = false

		for _, alloc := range rawAllocations {
			if alloc.Project == rootGrantGiver {
				_, alreadyKnown := rawAllocationPercentages[alloc.WalletId]
				if !alreadyKnown {
					rawAllocationPercentages[alloc.WalletId] = 1
					didChange = true
				}
			} else {
				parentPercent, hasParent := rawAllocationPercentages[alloc.Parent]
				_, hasAllocated := rawAllocationPercentages[alloc.WalletId]
				if hasParent && !hasAllocated {
					quotaInWallet := int64(0)
					for _, quota := range walletSumByParent[alloc.WalletId] {
						quotaInWallet += quota
					}

					quotaInParent := walletSumByParent[alloc.WalletId][alloc.Parent]
					if quotaInWallet != 0 {
						totalByCategory[alloc.Category] = totalByCategory[alloc.Category] + quotaInWallet
						totalFromGrantGiverByCategory[alloc.Category] = totalFromGrantGiverByCategory[alloc.Category] + quotaInParent

						rawAllocationPercentages[alloc.WalletId] = float64(quotaInParent) / float64(quotaInWallet) * parentPercent
					} else {
						rawAllocationPercentages[alloc.WalletId] = 0
					}

					didChange = true
				}
			}
		}
	}

	tempSum := float64(0)
	tempCount := 0

	weightedTotal := float64(0)
	weightedGrantGiver := float64(0)
	for _, alloc := range rawAllocations {
		walletPercent, ok := rawAllocationPercentages[alloc.WalletId]
		if ok {
			current, ok := allocationPercentages[alloc.Category]
			if !ok {
				current = map[string]float64{}
				allocationPercentages[alloc.Category] = current
			}

			weightedTotal += float64(alloc.Quota)

			if _, present := current[alloc.Project]; !present {
				current[alloc.Project] = walletPercent
				tempSum += walletPercent
				tempCount++

				weightedGrantGiver += float64(alloc.Quota) * walletPercent
			}
		}
	}

	// Normalize and filter categories
	// -----------------------------------------------------------------------------------------------------------------

	type normalizedSample struct {
		Usage     float64
		Timestamp time.Time
	}

	type projectSamples struct {
		ProjectId string
		Category  string
		Samples   []normalizedSample
	}

	type categorySamples struct {
		Unit     string
		Projects map[string]*projectSamples
	}

	oldestSample := end
	newestSample := start

	samplesByCategory := map[string]*categorySamples{}
	for _, s := range rawSamples {
		conversionFactor, ok := unitConversionTable[s.Category]
		if !ok {
			continue
		}

		unit, ok := unitTable[s.Category]
		if !ok {
			continue
		}

		category := samplesByCategory[s.Category]
		if category == nil {
			category = &categorySamples{Projects: make(map[string]*projectSamples)}
		}

		category.Unit = unit

		p := category.Projects[s.Project]
		if p == nil {
			p = &projectSamples{
				ProjectId: s.Project,
				Category:  s.Category,
			}
		}

		percent, ok := allocationPercentages[s.Category][s.Project]
		if ok {
			usageToGrantGiver := float64(s.LocalUsage) * conversionFactor * percent
			p.Samples = append(p.Samples, normalizedSample{
				Usage:     usageToGrantGiver,
				Timestamp: time.UnixMilli(s.Timestamp),
			})

			sTimestamp := time.UnixMilli(s.Timestamp)

			if sTimestamp.Before(oldestSample) {
				oldestSample = sTimestamp
			}

			if sTimestamp.After(newestSample) {
				newestSample = sTimestamp
			}

			category.Projects[s.Project] = p
			samplesByCategory[s.Category] = category
		}
	}

	for _, c := range samplesByCategory {
		for _, p := range c.Projects {
			slices.SortFunc(p.Samples, func(a, b normalizedSample) int {
				return a.Timestamp.Compare(b.Timestamp)
			})
		}
	}

	// Build user- and center reports
	// -----------------------------------------------------------------------------------------------------------------
	centerPeriod := ServiceProviderReport{
		Provider:  provider,
		Start:     start,
		End:       end,
		Resources: map[string]ResourceUseAndCapacity{},
	}

	centerDaily := map[time.Time]*ServiceProviderReport{}

	type userReports struct {
		Info   projectMember
		Daily  []*UserReport
		Period *UserReport
	}

	type projectReport struct {
		Users map[string]*userReports
	}

	var reportsByCategory struct {
		Categories map[string]map[string]*projectReport
	}
	reportsByCategory.Categories = make(map[string]map[string]*projectReport)

	for category, bucket := range samplesByCategory {
		isCapacityBased := bucket.Unit == storage
		catInfo := samplesByCategory[category]

		catReport, ok := reportsByCategory.Categories[category]
		if !ok {
			catReport = make(map[string]*projectReport)
			reportsByCategory.Categories[category] = catReport
		}

		for _, p := range projects {
			pSamples, ok := bucket.Projects[p.Id]
			if !ok {
				pSamples = &projectSamples{
					ProjectId: p.Id,
					Category:  category,
				}
			}

			pReport, ok := catReport[p.Id]
			if !ok {
				pReport = &projectReport{
					Users: make(map[string]*userReports),
				}
				catReport[p.Id] = pReport
			}

			currentDate := start
			sampleStartIndex := 0

			for currentDate.Before(end) {
				nextDate := currentDate.AddDate(0, 0, 1)
				sampleEndIndex := len(pSamples.Samples)

				for i := sampleStartIndex + 1; i < len(pSamples.Samples); i++ {
					if pSamples.Samples[i].Timestamp.After(nextDate) {
						sampleEndIndex = i
						break
					}
				}

				relevantSamples := pSamples.Samples[sampleStartIndex:min(sampleEndIndex+1, len(pSamples.Samples))]
				sampleStartIndex = sampleEndIndex

				dailyUsage := float64(0)

				if len(relevantSamples) > 0 {
					if isCapacityBased {
						dailyUsage = relevantSamples[len(relevantSamples)-1].Usage
					} else {
						dailyUsage = relevantSamples[len(relevantSamples)-1].Usage - relevantSamples[0].Usage
						if dailyUsage < 0 {
							dailyUsage = 0 // Compensate for expired allocations. Better data will come from Core2.
						}
					}
				}

				for _, m := range p.Members {
					uReport, ok := pReport.Users[m.Info.UserId]
					if !ok {
						uReport = &userReports{
							Info: m.Info,
							Period: &UserReport{
								Id:           m.Info.UserId,
								Organization: m.Info.OrgId,
								Project:      p.Id,
								GrantGiver:   rootGrantGiver,
								Start:        start,
								End:          end,
								Resources:    map[string]ResourceUseAndCapacity{},
							},
						}
						pReport.Users[m.Info.UserId] = uReport
					}

					report := &UserReport{
						Id:           m.Info.UserId,
						Organization: m.Info.OrgId,
						Project:      p.Id,
						GrantGiver:   rootGrantGiver,
						Start:        currentDate,
						End:          nextDate,
						Resources:    map[string]ResourceUseAndCapacity{},
					}
					report.Resources[category] = ResourceUseAndCapacity{
						Usage: m.Fraction * dailyUsage,
						Unit:  catInfo.Unit,
					}

					currentPeriodUsage := uReport.Period.Resources[category]
					uReport.Period.Resources[category] = ResourceUseAndCapacity{
						Usage: currentPeriodUsage.Usage + (m.Fraction * dailyUsage),
						Unit:  catInfo.Unit,
					}

					uReport.Daily = append(uReport.Daily, report)
				}

				centerDay, ok := centerDaily[currentDate]
				if !ok {
					centerDay = &ServiceProviderReport{
						Provider:  provider,
						Start:     currentDate,
						End:       nextDate,
						Resources: map[string]ResourceUseAndCapacity{},
					}

					centerDaily[currentDate] = centerDay
				}

				centerDay.Resources[category] = ResourceUseAndCapacity{
					Usage: centerDay.Resources[category].Usage + dailyUsage,
					Unit:  catInfo.Unit,
				}

				_ = sampleEndIndex

				currentDate = nextDate
			}
		}
	}

	// Collapse user reports
	// -----------------------------------------------------------------------------------------------------------------

	personDaily := map[personKey]map[time.Time]*UserReport{}
	personPeriod := map[personKey]*UserReport{}

	for _, byProject := range reportsByCategory.Categories {
		for projectId, report := range byProject {
			for userId, userReport := range report.Users {
				key := personKey{
					Username: userId,
					Project:  projectId,
				}

				// Daily
				dailyReports, ok := personDaily[key]
				if !ok {
					dailyReports = map[time.Time]*UserReport{}
					personDaily[key] = dailyReports
				}

				for _, d := range userReport.Daily {
					dailyReport, ok := dailyReports[d.Start]
					if !ok {
						dailyReport = &UserReport{
							Id:           d.Id,
							Organization: d.Organization,
							Project:      d.Project,
							GrantGiver:   d.GrantGiver,
							Start:        d.Start,
							End:          d.End,
							Resources:    map[string]ResourceUseAndCapacity{},
						}

						dailyReports[d.Start] = dailyReport
					}

					for k, v := range d.Resources {
						dailyReport.Resources[k] = v
					}
				}

				// Period
				periodReport, ok := personPeriod[key]
				if !ok {
					periodReport = &UserReport{
						Id:           userReport.Period.Id,
						Organization: userReport.Period.Organization,
						Project:      userReport.Period.Project,
						GrantGiver:   userReport.Period.GrantGiver,
						Start:        userReport.Period.Start,
						End:          userReport.Period.End,
						Resources:    map[string]ResourceUseAndCapacity{},
					}

					personPeriod[key] = periodReport
				}

				for k, v := range userReport.Period.Resources {
					periodReport.Resources[k] = v
				}
			}
		}
	}

	// Collapse center daily reports
	// -----------------------------------------------------------------------------------------------------------------
	{
		oldest := start.AddDate(-1, 0, 0)

		for timestamp, dailyReport := range centerDaily {
			for category, usage := range dailyReport.Resources {
				isCapacityBased := usage.Unit == storage

				if !isCapacityBased {
					centerPeriod.Resources[category] = ResourceUseAndCapacity{
						Usage: centerPeriod.Resources[category].Usage + usage.Usage,
						Unit:  usage.Unit,
					}
				} else if timestamp.After(oldest) {
					oldest = timestamp

					centerPeriod.Resources[category] = ResourceUseAndCapacity{
						Usage: usage.Usage,
						Unit:  usage.Unit,
					}
				}
			}
		}
	}

	return reportByGrantGiver{
		GrantGiver:   rootGrantGiver,
		CenterDaily:  centerDaily,
		CenterPeriod: centerPeriod,
		PersonDaily:  personDaily,
		PersonPeriod: personPeriod,
	}
}

const coreHours = "Core-hours"
const gpuHours = "GPU-hours"
const storage = "GB"
