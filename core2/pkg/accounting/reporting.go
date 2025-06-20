package accounting

import (
	"bytes"
	"encoding/gob"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"time"
	db "ucloud.dk/shared/pkg/database"
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

func reportingFetchAndDumpWallets(provider string, start, end time.Time) []byte {
	samples := db.NewTx(func(tx *db.Transaction) []reportingSample {
		return db.Select[reportingSample](
			tx,
			`
				select
					cast(extract(epoch from s.sampled_at) * 1000 as int8) as timestamp, 
					s.wallet_id,
					s.quota,
					s.local_usage,
					s.tree_usage,
					to_jsonb(s.tree_usage_by_group) as usage_by_group,
					p.id as project,
					pc.category,
					pc.provider
				from
					accounting.wallet_samples_v2 s
					join accounting.wallets_v2 w on s.wallet_id = w.id
					join accounting.wallet_owner wo on w.wallet_owner = wo.id
					join project.projects p on wo.project_id = p.id
					join accounting.product_categories pc on w.product_category = pc.id
				where
					pc.provider = :provider
					and s.sampled_at >= to_timestamp(:start / 1000.0)
					and s.sampled_at <= to_timestamp(:end / 1000.0)
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(samples)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}

type reportingAllocation struct {
	Id       int64
	Parent   int64
	Start    time.Time
	End      time.Time
	Quota    int64
	Category string
	Provider string
	Project  string
}

func reportingFetchAndDumpAllocations(provider string, start, end time.Time) []byte {
	allocations := db.NewTx(func(tx *db.Transaction) []reportingAllocation {
		return db.Select[reportingAllocation](
			tx,
			`
				select
					alloc.id,
					alloc.allocation_start_time as start,
					alloc.allocation_end_time as "end",
					alloc.quota,
					coalesce(ag.parent_wallet, 0) as parent,
					pc.category,
					pc.provider,
					p.id as project
				from
					accounting.wallet_allocations_v2 alloc
					join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
					join accounting.wallets_v2 w on ag.associated_wallet = w.id
					join accounting.product_categories pc on w.product_category = pc.id
					join accounting.wallet_owner wo on w.wallet_owner = wo.id
					join project.projects p on wo.project_id = p.id
				where
					pc.provider = :provider
					and alloc.allocation_start_time <= to_timestamp(:end / 1000.0)
					and to_timestamp(:start / 1000.0) <= allocation_end_time
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(allocations)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}

type reportingProjectMember struct {
	UserId  string
	Project string
	OrgId   string
	JobRuns int64
}

func reportingFetchAndDumpMembers(provider string, start, end time.Time) []byte {
	members := db.NewTx(func(tx *db.Transaction) []reportingProjectMember {
		return db.Select[reportingProjectMember](
			tx,
			`
				with
					job_launches as (
						select
							pm.username as user_id,
							pm.project_id as project,
							count(r.id) as job_runs
						from
							project.project_members pm
							left join provider.resource r on pm.project_id = r.project and pm.username = r.created_by
							left join accounting.products p on r.product = p.id
							left join accounting.product_categories pc on p.category = pc.id
						where
							r.type = 'job'
							and r.created_at >= to_timestamp(:start / 1000.0)
							and r.created_at <= to_timestamp(:end / 1000.0)
							and pc.provider = :provider
						group by pm.username, pm.project_id
					)
				select distinct 
					pm.username as user_id, 
					pm.project_id as project, 
					coalesce(jl.job_runs, 0) as job_runs
				from
					project.project_members pm
					join project.projects p on pm.project_id = p.id
					join accounting.wallet_owner wo on p.id = wo.project_id
					join accounting.wallets_v2 w on wo.id = w.wallet_owner
					join accounting.allocation_groups ag on w.id = ag.associated_wallet
					join accounting.wallet_allocations_v2 alloc on ag.id = alloc.associated_allocation_group
					join accounting.product_categories pc on w.product_category = pc.id
					left join job_launches jl on jl.user_id = pm.username and jl.project = pm.project_id
				where
					pc.provider = :provider;
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(members)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}

func RunReporting(provider string, start, end time.Time, dumpOnly bool, noDump bool, dir string) {
	var rawMembers []reportingProjectMember
	var rawAllocations []reportingAllocation
	var rawSamples []reportingSample

	if dumpOnly || !noDump {
		fmt.Printf("Dumping project members...\n")
		memberBytes := reportingFetchAndDumpMembers(provider, start, end)
		fmt.Printf("Dumping allocations...\n")
		allocBytes := reportingFetchAndDumpAllocations(provider, start, end)
		fmt.Printf("Dumping wallet samples...\n")
		sampleBytes := reportingFetchAndDumpWallets(provider, start, end)
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
	} else if noDump {
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
	type projectMember struct {
		Info     reportingProjectMember
		Fraction float64 // 0 to 1
	}

	type project struct {
		Id      string
		Members []projectMember
	}

	projects := map[string]*project{}

	for _, member := range rawMembers {
		p := projects[member.Project]
		if p == nil {
			p = &project{}
		}

		p.Id = member.Project

		if member.JobRuns == 0 {
			member.JobRuns = 1 // compensate for usage fraction for storage only users by giving them at least 1 job
		}

		p.Members = append(p.Members, projectMember{
			Info:     member,
			Fraction: 0})

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

	// Normalize and filter categories
	// -----------------------------------------------------------------------------------------------------------------
	unitTable := map[string]string{
		"u1-standard":   reportingCoreHours,
		"u1-standard-h": reportingCoreHours,
		"u1-gpu":        reportingGpuHours,
		"u1-gpu-h":      reportingGpuHours,
		"u1-fat-h":      reportingCoreHours,
		"u2-gpu-h":      reportingGpuHours,
		"u3-gpu":        reportingGpuHours,
		"u1-cephfs":     reportingStorage,
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

		// TODO Just do the per grant giver filtering immediately here?
		p.Samples = append(p.Samples, normalizedSample{
			Usage:     float64(s.TreeUsage) * conversionFactor, // todo by group
			Timestamp: time.UnixMilli(s.Timestamp),
		})

		category.Projects[s.Project] = p
		samplesByCategory[s.Category] = category
	}

	for _, c := range samplesByCategory {
		for _, p := range c.Projects {
			slices.SortFunc(p.Samples, func(a, b normalizedSample) int {
				return a.Timestamp.Compare(b.Timestamp)
			})
		}
	}

	// Build user reports
	// -----------------------------------------------------------------------------------------------------------------
	type userReports struct {
		Info   reportingProjectMember
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

				relevantSamples := pSamples.Samples[sampleStartIndex:sampleEndIndex]
				sampleStartIndex = sampleEndIndex

				isCapacityBased := bucket.Unit == reportingStorage
				dailyUsage := float64(0)

				if len(relevantSamples) > 0 {
					if isCapacityBased {
						dailyUsage = relevantSamples[len(relevantSamples)-1].Usage
					} else {
						dailyUsage = relevantSamples[len(relevantSamples)-1].Usage - relevantSamples[0].Usage
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
								Start:        currentDate,
								End:          nextDate,
								Resources:    map[string]ResourceUseAndCapacity{},
							},
						}
						pReport.Users[m.Info.UserId] = uReport
					}

					report := &UserReport{
						Id:           m.Info.UserId,
						Organization: m.Info.OrgId,
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

				_ = sampleEndIndex

				currentDate = nextDate
			}
		}
	}

	fmt.Printf("Done\n")
}

const reportingCoreHours = "Core-hours"
const reportingGpuHours = "GPU-hours"
const reportingStorage = "GB"
