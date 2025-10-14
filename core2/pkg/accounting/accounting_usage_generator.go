package accounting

import (
	"fmt"
	"math/rand"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type UsageGenApi struct {
	AllocateEx  func(now, start, end int, quota int64, recipientRef, parentRef string)
	ReportDelta func(now int, ownerRef string, usage int64)
	Checkpoint  func(now int)
}

type UsageGenConfig struct {
	Days            int
	BreadthPerLevel []int
	Seed            int64
}

type UsageGenProject struct {
	Parent      string
	Title       string
	LocalUsage  int64
	LocalUsage2 int64
	Quota       int64
	Level       int
	Children    []*UsageGenProject
}

type usageGenerator struct {
	Api  UsageGenApi
	Rng  *rand.Rand
	Cfg  UsageGenConfig
	Root *UsageGenProject
}

type usageGenJob struct {
	StartMinute int
	EndMinute   int
	CoreCount   int
}

func UsageGenGenerate(api UsageGenApi, cfg UsageGenConfig) *UsageGenProject {
	g := &usageGenerator{
		Api: api,
		Rng: rand.New(rand.NewSource(cfg.Seed)),
		Cfg: cfg,
		Root: &UsageGenProject{
			Parent: "",
			Title:  "UGTest",
			Level:  -1,
			Quota:  100_000_000_000,
		},
	}

	g.Api.AllocateEx(0, 0, 1440*cfg.Days, g.Root.Quota, g.Root.Title, g.Root.Parent)

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

		activeJobsPerProject := map[string][]usageGenJob{}
		for _, project := range activeProjectsToday {
			minutesRemainingOverall := project.Quota
			if len(project.Children) > 0 {
				minutesRemainingOverall /= 2
			}

			minutesRemainingOverall -= project.LocalUsage

			if minutesRemainingOverall <= 0 {
				continue
			}

			jobsToday := 1 + g.Rng.Intn(4)
			for i := 0; i < jobsToday; i++ {
				coreCountsToSample := []int{1, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4, 8, 16, 32, 32, 32,
					32, 32, 32, 32, 64, 64, 64, 64, 64, 64, 64, 64, 128, 256, 512, 1024}

				durationMinutes := 0
				coreCount := coreCountsToSample[g.Rng.Intn(len(coreCountsToSample))]

				{
					u := g.Rng.Float64()
					if u < 0.8 {
						durationMinutes = 30 + g.Rng.Intn(450) // 0.5 hours to 8 hours
					} else if u < 0.99 {
						durationMinutes = 240 + g.Rng.Intn(720) // 4 hours to 16 hours
					} else {
						durationMinutes = 960 + g.Rng.Intn(240) // 16 hours to 20 hours
					}
				}

				if isWeekend {
					durationMinutes = int(float64(durationMinutes) * 0.6)
				}

				if int(minutesRemainingOverall) < durationMinutes*coreCount {
					durationMinutes = int(minutesRemainingOverall) / coreCount
				}

				if durationMinutes > 0 {
					startOfDay := 0
					{
						u := g.Rng.Float64()
						if u < 0.45 {
							// Peak at 09:00-10:00
							startOfDay = 9*60 + g.Rng.Intn(60)
						} else if u < 0.9 {
							// Peak at 13:00-14:00
							startOfDay = 13*60 + g.Rng.Intn(60)
						} else {
							// Random throughout the day
							startOfDay = g.Rng.Intn(1440)
						}
					}

					if startOfDay+durationMinutes > 1440 {
						startOfDay = 1440 - durationMinutes
					}

					project.LocalUsage += int64(durationMinutes * coreCount)
					activeJobsPerProject[project.Title] = append(activeJobsPerProject[project.Title], usageGenJob{
						StartMinute: startOfDay,
						EndMinute:   startOfDay + durationMinutes,
						CoreCount:   coreCount,
					})
					jobsCreated++
				}
			}
		}

		startOfDay := day * 1440
		endOfDay := (day + 1) * 1440
		for minute := startOfDay; minute <= endOfDay; minute += 5 {
			minuteOfDay := minute - startOfDay

			for _, project := range activeProjectsToday {
				myJobs := activeJobsPerProject[project.Title]
				for _, job := range myJobs {
					// tick window is [minuteOfDay, minuteOfDay+5)
					tickStart := minuteOfDay
					tickEnd := minuteOfDay + 5

					// overlap with job window [StartMinute, EndMinute)
					overlapStart := tickStart
					if job.StartMinute > overlapStart {
						overlapStart = job.StartMinute
					}
					overlapEnd := tickEnd
					if job.EndMinute < overlapEnd {
						overlapEnd = job.EndMinute
					}

					if overlapEnd > overlapStart {
						slice := overlapEnd - overlapStart // 1..5 minutes
						usageInPeriod := int64(slice * job.CoreCount)
						project.LocalUsage2 += usageInPeriod
						api.ReportDelta(minute, project.Title, usageInPeriod)
					}
				}
			}
		}

		api.Checkpoint(endOfDay)
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
		child := &UsageGenProject{
			Parent:   parent.Title,
			Title:    baseTitle + fmt.Sprint(i),
			Quota:    int64(float64(parent.Quota) * weights[i] / 2.0),
			Level:    parent.Level + 1,
			Children: nil,
		}
		parent.Children = append(parent.Children, child)

		g.Api.AllocateEx(0, 0, 1440*g.Cfg.Days, child.Quota, child.Title, child.Parent)
	}
}
