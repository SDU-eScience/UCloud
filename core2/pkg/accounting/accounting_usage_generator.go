package accounting

import (
	"fmt"
	"math/rand"
	"sort"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type UsageGenProduct int

const (
	UsageGenProductCPUOne UsageGenProduct = iota
	UsageGenProductCPUTwo
	UsageGenProductStorage
)

type UsageGenApi struct {
	AllocateEx  func(product UsageGenProduct, now, start, end int, quota int64, recipientRef, parentRef string)
	ReportDelta func(product UsageGenProduct, now int, ownerRef string, usage int64)
	Checkpoint  func(now int)
}

type storageTrend int

const (
	storageStable storageTrend = iota
	storageGrowing
	storageShrinking
)

type UsageGenProject struct {
	Parent string
	Title  string

	CPUGeneratedOne int64
	CPUReportedOne  int64

	CPUGeneratedTwo int64
	CPUReportedTwo  int64

	CPUQuota int64

	StorageUsage  int64
	StorageUsage2 int64
	StorageQuota  int64

	StorageTrend     storageTrend
	StorageTrendDays int

	Level    int
	Children []*UsageGenProject
}

type usageGenerator struct {
	Api  UsageGenApi
	Rng  *rand.Rand
	Cfg  accapi.UsageGenConfig
	Root *UsageGenProject
}

type usageGenStorageEvent struct {
	Minute int
	Delta  int64
}

func storageGrowthDelta(g *usageGenerator, quota int64) int64 {
	// Normal growth is 1-20% of quota.
	minimum := max(1, quota/100)
	maximum := max(minimum+1, quota/5)

	delta := minimum + g.Rng.Int63n(maximum-minimum)

	// Occasionally grow past quota to test over-quota usage.
	if g.Rng.Float64() < 0.10 {
		delta += quota / 20 // +5% quota
	}

	return delta
}

func storageReleaseDelta(g *usageGenerator, currentUsage int64) int64 {
	if currentUsage <= 0 {
		return 0
	}

	minimum := int64(1)
	maximum := max(minimum+1, currentUsage/2)

	return minimum + g.Rng.Int63n(maximum-minimum)
}

func chooseStorageTrend(g *usageGenerator, project *UsageGenProject) {
	// Keep the current trend for several days.
	if project.StorageTrendDays > 0 {
		project.StorageTrendDays--
		return
	}

	switch {
	case project.StorageUsage < project.StorageQuota/10:
		project.StorageTrend = storageGrowing

	case project.StorageUsage > project.StorageQuota*12/10:
		project.StorageTrend = storageShrinking

	default:
		switch g.Rng.Intn(3) {
		case 0:
			project.StorageTrend = storageGrowing
		case 1:
			project.StorageTrend = storageStable
		case 2:
			project.StorageTrend = storageShrinking
		}
	}

	// Keep this trend for 3-5 days.
	project.StorageTrendDays = 3 + g.Rng.Intn(2)
}

func usageGenGenerateStorage(
	g *usageGenerator,
	project *UsageGenProject,
	startMinute int,
	endMinute int,
) []usageGenStorageEvent {
	quota := project.StorageQuota
	if quota <= 0 {
		return nil
	}

	eventCount := 1 + g.Rng.Intn(9)

	minutes := make([]int, eventCount)
	for i := range minutes {
		minutes[i] = startMinute + g.Rng.Intn(max(1, endMinute-startMinute))
	}

	sort.Ints(minutes)

	events := make([]usageGenStorageEvent, 0, eventCount)
	currentUsage := project.StorageUsage

	for _, minute := range minutes {
		var delta int64

		switch project.StorageTrend {
		case storageGrowing:
			delta = storageGrowthDelta(g, quota)

		case storageShrinking:
			delta = -storageReleaseDelta(g, currentUsage)

		case storageStable:
			if g.Rng.Float64() < 0.5 {
				delta = storageGrowthDelta(g, quota) / 4
			} else {
				delta = -storageReleaseDelta(g, currentUsage) / 4
			}
		}

		if currentUsage+delta < 0 {
			delta = -currentUsage
		}

		currentUsage += delta

		events = append(events, usageGenStorageEvent{
			Minute: minute,
			Delta:  delta,
		})
	}

	return events
}

type usageGenJob struct {
	StartMinute int
	EndMinute   int
	CoreCount   int
}

func usageGenRandomJob(
	g *usageGenerator,
	minutesRemaining int64,
	isWeekend bool,
) usageGenJob {
	coreCountsToSample := []int{
		1,
		2, 2, 2, 2, 2, 2, 2,
		4, 4, 4, 4, 4, 4, 4, 4, 4,
		8,
		16,
		32, 32, 32, 32, 32, 32, 32,
		64, 64, 64, 64, 64, 64, 64,
		128,
		256,
		512,
		1024,
	}

	coreCount := coreCountsToSample[g.Rng.Intn(len(coreCountsToSample))]

	var durationMinutes int

	u := g.Rng.Float64()
	if u < 0.8 {
		durationMinutes = 30 + g.Rng.Intn(450)
	} else if u < 0.99 {
		durationMinutes = 240 + g.Rng.Intn(720)
	} else {
		durationMinutes = 960 + g.Rng.Intn(240)
	}

	if isWeekend {
		durationMinutes = int(float64(durationMinutes) * 0.6)
	}

	if minutesRemaining < int64(durationMinutes*coreCount) {
		durationMinutes = int(minutesRemaining) / coreCount
	}

	if durationMinutes <= 0 {
		return usageGenJob{}
	}

	startOfDay := 0

	u = g.Rng.Float64()
	if u < 0.45 {
		startOfDay = 9*60 + g.Rng.Intn(60)
	} else if u < 0.9 {
		startOfDay = 13*60 + g.Rng.Intn(60)
	} else {
		startOfDay = g.Rng.Intn(1440)
	}

	if startOfDay+durationMinutes > 1440 {
		startOfDay = 1440 - durationMinutes
	}

	return usageGenJob{
		StartMinute: startOfDay,
		EndMinute:   startOfDay + durationMinutes,
		CoreCount:   coreCount,
	}
}

func UsageGenGenerate(api UsageGenApi, cfg accapi.UsageGenConfig) *UsageGenProject {
	g := &usageGenerator{
		Api: api,
		Rng: rand.New(rand.NewSource(cfg.Seed)),
		Cfg: cfg,
		Root: &UsageGenProject{
			Parent:   "",
			Title:    "UGTest",
			Level:    -1,
			CPUQuota: 600_000 * int64(cfg.Days),
		},
	}

	g.Api.AllocateEx(UsageGenProductCPUOne, 0, 0, 1440*cfg.Days, g.Root.CPUQuota, g.Root.Title, g.Root.Parent)
	g.Api.AllocateEx(UsageGenProductCPUTwo, 0, 0, 1440*cfg.Days, g.Root.CPUQuota, g.Root.Title, g.Root.Parent)

	storageRootQuota := int64(100_000)

	g.Root.StorageQuota = storageRootQuota

	g.Api.AllocateEx(UsageGenProductStorage, 0, 0, 1440*cfg.Days, storageRootQuota, g.Root.Title, g.Root.Parent)

	{
		// Generate projects
		projectsCreated := 0

		var stack []*UsageGenProject
		stack = append(stack, g.Root)

		for len(stack) > 0 {
			var next *UsageGenProject
			next, stack = util.PopHead(stack)

			if next.Level+1 < len(cfg.BreadthPerLevel) {
				baseCount := cfg.BreadthPerLevel[next.Level+1]
				minCount := int(float64(baseCount) * 0.9)
				maxCount := int(float64(baseCount) * 1.1)

				count := max(1, minCount+g.Rng.Intn(maxCount-minCount))
				usageGenAllocateProjects(g, next, count)

				for _, child := range next.Children {
					stack = append(stack, child)
					projectsCreated++
				}
			}
		}

		log.Info("Created %v projects", projectsCreated)
	}

	jobsCreated := 0

	for day := 0; day < cfg.Days; day++ {
		log.Info("Simulating day %v of %v", day+1, cfg.Days)
		dayOfWeek := day % 7
		isWeekend := dayOfWeek >= 5

		var activeProjectsToday []*UsageGenProject

		{
			// Select active projects for the day

			var stack []*UsageGenProject
			stack = append(stack, g.Root)

			for len(stack) > 0 {
				var next *UsageGenProject
				next, stack = util.PopHead(stack)

				for _, child := range next.Children {
					stack = append(stack, child)
				}

				if next.Level > 1 {
					isActive := false
					if isWeekend && g.Rng.Float64() <= 0.05 {
						isActive = true
					} else if !isWeekend && g.Rng.Float64() <= 0.10 {
						isActive = true
					}

					if isActive {
						activeProjectsToday = append(activeProjectsToday, next)
					}
				}
			}
		}

		activeJobsPerProjectOne := map[string][]usageGenJob{}
		activeJobsPerProjectTwo := map[string][]usageGenJob{}
		for _, project := range activeProjectsToday {
			minutesRemainingOverall := project.CPUQuota

			if len(project.Children) > 0 {
				minutesRemainingOverall /= 2
			}

			minutesRemainingOverall -= project.CPUGeneratedOne

			if minutesRemainingOverall > 0 {
				jobsToday := 1 + g.Rng.Intn(4)

				for i := 0; i < jobsToday; i++ {
					job := usageGenRandomJob(g, minutesRemainingOverall, isWeekend)

					if job.EndMinute > job.StartMinute {
						usage := int64(job.EndMinute-job.StartMinute) * int64(job.CoreCount)

						project.CPUGeneratedOne += usage
						activeJobsPerProjectOne[project.Title] =
							append(activeJobsPerProjectOne[project.Title], job)

						jobsCreated++
					}
				}
			}

			// Generate an independent set of jobs for CPU Two.
			minutesRemainingOverall = project.CPUQuota

			if len(project.Children) > 0 {
				minutesRemainingOverall /= 2
			}

			minutesRemainingOverall -= project.CPUGeneratedTwo

			if minutesRemainingOverall > 0 {
				jobsToday := 1 + g.Rng.Intn(4)

				for i := 0; i < jobsToday; i++ {
					job := usageGenRandomJob(g, minutesRemainingOverall, isWeekend)

					if job.EndMinute > job.StartMinute {
						usage := int64(job.EndMinute-job.StartMinute) * int64(job.CoreCount)

						project.CPUGeneratedTwo += usage
						activeJobsPerProjectTwo[project.Title] =
							append(activeJobsPerProjectTwo[project.Title], job)

						jobsCreated++
					}
				}
			}
		}

		startOfDay := day * 1440
		endOfDay := (day + 1) * 1440

		storageEvents := map[string][]usageGenStorageEvent{}

		for _, project := range activeProjectsToday {
			chooseStorageTrend(g, project)

			storageEvents[project.Title] = usageGenGenerateStorage(
				g,
				project,
				startOfDay,
				endOfDay,
			)
		}

		minuteStep := g.Cfg.ReportingInterval
		if minuteStep == 0 {
			minuteStep = 5
		}

		checkpointInterval := cfg.CheckpointInterval
		if checkpointInterval == 0 {
			checkpointInterval = 60 // whatever default makes sense
		}

		for minute := startOfDay; minute <= endOfDay; minute += minuteStep {
			minuteOfDay := minute - startOfDay

			for _, project := range activeProjectsToday {
				myJobs := activeJobsPerProjectOne[project.Title]
				for _, job := range myJobs {
					tickStart := minuteOfDay
					tickEnd := minuteOfDay + minuteStep

					overlapStart := max(job.StartMinute, tickStart)
					overlapEnd := min(job.EndMinute, tickEnd)

					if overlapEnd > overlapStart {
						slice := overlapEnd - overlapStart
						usageInPeriod := int64(slice * job.CoreCount)
						project.CPUReportedOne += usageInPeriod
						api.ReportDelta(UsageGenProductCPUOne, minute, project.Title, usageInPeriod)
					}
				}

				myJobs = activeJobsPerProjectTwo[project.Title]
				for _, job := range myJobs {
					tickStart := minuteOfDay
					tickEnd := minuteOfDay + minuteStep

					overlapStart := max(job.StartMinute, tickStart)
					overlapEnd := min(job.EndMinute, tickEnd)

					if overlapEnd > overlapStart {
						slice := overlapEnd - overlapStart
						usageInPeriod := int64(slice * job.CoreCount)
						project.CPUReportedTwo += usageInPeriod
						api.ReportDelta(UsageGenProductCPUTwo, minute, project.Title, usageInPeriod)
					}
				}

				for _, event := range storageEvents[project.Title] {
					if event.Minute < minute {
						continue
					}

					if event.Minute >= minute+minuteStep {
						break
					}

					delta := event.Delta

					if delta < 0 {
						maxRelease := project.StorageUsage
						if -delta > maxRelease {
							delta = -maxRelease
						}
					}

					if delta == 0 {
						continue
					}

					project.StorageUsage += delta
					project.StorageUsage2 += delta

					api.ReportDelta(
						UsageGenProductStorage,
						minute,
						project.Title,
						delta,
					)
				}
			}

			if minute%cfg.CheckpointInterval == 0 {
				api.Checkpoint(minute)
			}
		}
	}

	log.Info("Jobs created: %v", jobsCreated)

	return g.Root
}

func usageGenAllocateProjects(g *usageGenerator, parent *UsageGenProject, breadth int) {
	weights := make([]float64, breadth)

	{
		sum := 0.0
		for i := range weights {
			w := g.Rng.ExpFloat64()
			weights[i] = w
			sum += w
		}

		for i := range weights {
			weights[i] /= sum
		}
	}

	baseTitle := parent.Title + "_"

	for i := 0; i < breadth; i++ {
		cpuQuota := int64(float64(parent.CPUQuota) * weights[i] / 2.0)
		storageQuota := int64(float64(parent.StorageQuota) * weights[i] / 2.0)

		child := &UsageGenProject{
			Parent: parent.Title,
			Title:  baseTitle + fmt.Sprint(i),

			CPUQuota:     cpuQuota,
			StorageQuota: storageQuota,

			Level: parent.Level + 1,
		}

		parent.Children = append(parent.Children, child)

		if g.Cfg.Expiration {
			currentDay := 0
			remainingDays := g.Cfg.Days

			for remainingDays > 0 {
				count := min(remainingDays, 1+g.Rng.Intn(2))

				start := 1440 * currentDay
				end := (1440 * (currentDay + count)) - 1

				g.Api.AllocateEx(
					UsageGenProductCPUOne,
					start,
					start,
					end,
					child.CPUQuota,
					child.Title,
					child.Parent,
				)

				g.Api.AllocateEx(
					UsageGenProductCPUTwo,
					start,
					start,
					end,
					child.CPUQuota,
					child.Title,
					child.Parent,
				)

				g.Api.AllocateEx(
					UsageGenProductStorage,
					start,
					start,
					end,
					child.StorageQuota,
					child.Title,
					child.Parent,
				)

				currentDay += count
				remainingDays -= count
			}
		} else {
			g.Api.AllocateEx(
				UsageGenProductCPUOne,
				0,
				0,
				1440*g.Cfg.Days,
				child.CPUQuota,
				child.Title,
				child.Parent,
			)

			g.Api.AllocateEx(
				UsageGenProductCPUTwo,
				0,
				0,
				1440*g.Cfg.Days,
				child.CPUQuota,
				child.Title,
				child.Parent,
			)

			g.Api.AllocateEx(
				UsageGenProductStorage,
				0,
				0,
				1440*g.Cfg.Days,
				child.StorageQuota,
				child.Title,
				child.Parent,
			)
		}
	}
}
