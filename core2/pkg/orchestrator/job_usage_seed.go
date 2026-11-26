package orchestrator

import (
	"fmt"
	"hash/fnv"
	"math"
	"math/rand/v2"
	"net/http"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type jobSeedMachineSlice struct {
	Cpu           int
	Gpu           int
	MemoryInGigs  int
	Units         int
	BaseQueueSecs float64
}

var jobSeedCpuSlices = []jobSeedMachineSlice{
	{Cpu: 4, Gpu: 0, MemoryInGigs: 24, Units: 4, BaseQueueSecs: 6},
	{Cpu: 16, Gpu: 0, MemoryInGigs: 96, Units: 16, BaseQueueSecs: 18},
	{Cpu: 32, Gpu: 0, MemoryInGigs: 192, Units: 32, BaseQueueSecs: 180},
	{Cpu: 64, Gpu: 0, MemoryInGigs: 384, Units: 64, BaseQueueSecs: 480},
}

var jobSeedGpuSlices = []jobSeedMachineSlice{
	{Cpu: 16, Gpu: 1, MemoryInGigs: 128, Units: 1, BaseQueueSecs: 480},
	{Cpu: 32, Gpu: 2, MemoryInGigs: 256, Units: 2, BaseQueueSecs: 1800},
	{Cpu: 64, Gpu: 4, MemoryInGigs: 512, Units: 4, BaseQueueSecs: 4500},
}

var jobSeedCpuApps = []string{
	"JupyterLab",
	"RStudio",
	"Bash",
	"VS Code",
	"Nextflow",
	"Snakemake",
}

var jobSeedGpuApps = []string{
	"PyTorch",
	"JupyterLab",
	"AlphaFold 3",
	"vLLM",
	"TensorFlow",
	"Bash",
}

func jobSeedProductName(category string, sl jobSeedMachineSlice, isGpu bool) string {
	count := sl.Cpu
	unit := "vcpu"
	if isGpu {
		count = sl.Gpu
		unit = "gpu"
	}
	base := category
	lastDash := strings.LastIndexByte(category, '-')
	if lastDash > 0 && len(category)-lastDash-1 == 12 {
		isHexToken := true
		for _, ch := range category[lastDash+1:] {
			isHexChar := (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
			if !isHexChar {
				isHexToken = false
				break
			}
		}
		if isHexToken {
			base = category + "-" + category[:lastDash]
		}
	}
	return fmt.Sprintf("%s-%d-%s", base, count, unit)
}

func jobSeedRng(parts ...string) *rand.Rand {
	h1 := fnv.New64a()
	h2 := fnv.New64()
	for _, part := range parts {
		_, _ = h1.Write([]byte(part))
		_, _ = h1.Write([]byte{0})
		_, _ = h2.Write([]byte(part))
		_, _ = h2.Write([]byte{1})
	}
	return rand.New(rand.NewPCG(h1.Sum64(), h2.Sum64()))
}

func jobHandleUsageBreakdownSeed(
	info rpc.RequestInfo,
	request orcapi.JobUsageBreakdownSeedInternalRequest,
) (util.Empty, *util.HttpError) {
	_ = info
	if !util.DevelopmentModeEnabled() {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "development mode required")
	}
	if len(request.Charges) == 0 {
		return util.Empty{}, nil
	}

	type catKey struct {
		Provider string
		Category string
	}
	categoryIsGpu := map[catKey]bool{}
	for _, charge := range request.Charges {
		p := strings.TrimSpace(charge.Provider)
		c := strings.TrimSpace(charge.Category)
		if p == "" || c == "" || charge.ComputeHours <= 0 {
			continue
		}
		categoryIsGpu[catKey{Provider: p, Category: c}] = charge.IsGpu
	}
	if len(categoryIsGpu) == 0 {
		return util.Empty{}, nil
	}

	type sliceProduct struct {
		ProductId int64
		Slice     jobSeedMachineSlice
	}
	productsByCategory := map[catKey][]sliceProduct{}

	var seedErr error
	var allocatedIds []int64
	now := time.Now().UTC()

	db.NewTx0(func(tx *db.Transaction) {
		for key, isGpu := range categoryIsGpu {
			catRow, hasCat := db.Get[struct {
				Id int64
			}](
				tx,
				`
					select id
					from accounting.product_categories
					where
						provider = :provider
						and category = :category
				`,
				db.Params{
					"provider": key.Provider,
					"category": key.Category,
				},
			)
			if !hasCat {
				continue
			}

			slices := jobSeedCpuSlices
			if isGpu {
				slices = jobSeedGpuSlices
			}

			list := make([]sliceProduct, 0, len(slices))
			for _, sl := range slices {
				prodName := jobSeedProductName(key.Category, sl, isGpu)
				prodRow, ok := db.Get[struct {
					Id int64
				}](
					tx,
					`
						insert into accounting.products(
							name,
							price,
							cpu,
							gpu,
							memory_in_gigs,
							category,
							version,
							description,
							hidden_in_grant_applications
						)
						values (
							:name,
							:price,
							:cpu,
							:gpu,
							:memory_in_gigs,
							:category,
							1,
							:description,
							true
						)
						on conflict (name, category, version) do update set
							cpu = excluded.cpu,
							gpu = excluded.gpu,
							memory_in_gigs = excluded.memory_in_gigs
						returning id
					`,
					db.Params{
						"name":           prodName,
						"price":          int64(sl.Units),
						"cpu":            sl.Cpu,
						"gpu":            sl.Gpu,
						"memory_in_gigs": sl.MemoryInGigs,
						"category":       catRow.Id,
						"description":    prodName,
					},
				)
				if ok {
					list = append(list, sliceProduct{
						ProductId: prodRow.Id,
						Slice:     sl,
					})
				}
			}
			productsByCategory[key] = list
		}

		resProviders := make([]string, 0, len(request.Charges)*2)
		resCreatedAt := make([]time.Time, 0, len(request.Charges)*2)
		resProjects := make([]string, 0, len(request.Charges)*2)
		resProducts := make([]int64, 0, len(request.Charges)*2)
		resGenIds := make([]string, 0, len(request.Charges)*2)

		jobApps := make([]string, 0, len(request.Charges)*2)
		jobTimeAllocs := make([]int64, 0, len(request.Charges)*2)
		jobReplicas := make([]int, 0, len(request.Charges)*2)
		jobNames := make([]string, 0, len(request.Charges)*2)
		jobStates := make([]string, 0, len(request.Charges)*2)
		jobStartedAt := make([]time.Time, 0, len(request.Charges)*2)
		jobLastUpdate := make([]time.Time, 0, len(request.Charges)*2)

		ownerPrimaryApp := map[string]int{}

		for chargeIdx, charge := range request.Charges {
			owner := strings.TrimSpace(charge.Owner)
			dayStr := strings.TrimSpace(charge.Day)
			if owner == "" || dayStr == "" || charge.ComputeHours <= 0 {
				continue
			}
			key := catKey{
				Provider: strings.TrimSpace(charge.Provider),
				Category: strings.TrimSpace(charge.Category),
			}
			availSlices := productsByCategory[key]
			if len(availSlices) == 0 {
				continue
			}
			dayTime, parseErr := time.Parse("2006-01-02", dayStr)
			if parseErr != nil {
				continue
			}
			dayTime = dayTime.UTC()

			apps := jobSeedCpuApps
			if charge.IsGpu {
				apps = jobSeedGpuApps
			}
			primaryAppIdx, hasPrimary := ownerPrimaryApp[owner]
			if !hasPrimary {
				ownerRng := jobSeedRng("job-owner", owner)
				primaryAppIdx = ownerRng.IntN(len(apps))
				ownerPrimaryApp[owner] = primaryAppIdx
			}

			rng := jobSeedRng("job-charge", key.Provider, key.Category, owner, dayStr)
			jobCount := 1 + rng.IntN(3)
			if charge.ComputeHours >= 200 {
				jobCount = 2 + rng.IntN(3)
			}
			if charge.ComputeHours >= 1500 {
				jobCount = 3 + rng.IntN(3)
			}

			weights := make([]float64, jobCount)
			weightSum := 0.0
			for j := 0; j < jobCount; j++ {
				w := 0.4 + rng.Float64()*1.2
				weights[j] = w
				weightSum += w
			}

			for j := 0; j < jobCount; j++ {
				targetHours := charge.ComputeHours * (weights[j] / weightSum)
				if targetHours < 0.05 {
					continue
				}

				sliceIdx := rng.IntN(len(availSlices))
				if targetHours < 8 {
					sliceIdx = 0
				} else if targetHours > 120 && len(availSlices) > 2 && rng.Float64() < 0.65 {
					sliceIdx = len(availSlices) - 1 - rng.IntN(2)
				}
				picked := availSlices[sliceIdx]

				replicas := 1
				if targetHours > float64(picked.Slice.Units)*24 {
					replicas = max(1, min(16, int(math.Ceil(targetHours/(float64(picked.Slice.Units)*18.0)))))
				}

				wallHours := targetHours / float64(picked.Slice.Units*replicas)
				if wallHours < 0.05 {
					wallHours = 0.05
				}
				if wallHours > 72 {
					wallHours = 72
				}

				appIdx := primaryAppIdx
				if rng.Float64() < 0.45 {
					appIdx = rng.IntN(len(apps))
				}
				appName := apps[appIdx]

				roll := rng.Float64()
				state := "SUCCESS"
				efficiency := 0.55 + rng.Float64()*0.40
				if roll < 0.015 {
					state = "FAILURE"
					efficiency = 0.15 + rng.Float64()*0.45
				} else if roll < 0.295 {
					state = "EXPIRED"
					efficiency = 1.0
				} else if roll < 0.335 && now.Sub(dayTime) < 48*time.Hour {
					state = "RUNNING"
					efficiency = 0.45 + rng.Float64()*0.35
				}

				requestedWallHours := wallHours / efficiency
				if requestedWallHours < wallHours {
					requestedWallHours = wallHours
				}
				timeAllocMillis := int64(math.Round(requestedWallHours * 3600000.0))

				queueSecs := picked.Slice.BaseQueueSecs * (0.25 + rng.Float64()*1.8)
				if replicas > 1 {
					replicaFactor := min(4.0, 1.0+0.35*float64(replicas-1))
					queueSecs *= replicaFactor
				}
				if rng.Float64() < 0.12 {
					queueSecs *= 2.0 + rng.Float64()*2.0
				}

				offsetSecs := 60 + rng.IntN(20*3600)
				createdAt := dayTime.Add(time.Duration(offsetSecs) * time.Second)
				if createdAt.After(now.Add(-5 * time.Minute)) {
					createdAt = now.Add(-5 * time.Minute)
				}
				startedAt := createdAt.Add(time.Duration(math.Round(queueSecs)) * time.Second)
				if startedAt.After(now) {
					startedAt = createdAt.Add(10 * time.Second)
				}
				finishedAt := startedAt.Add(time.Duration(math.Round(wallHours*3600.0)) * time.Second)
				if state == "RUNNING" && finishedAt.After(now) {
					finishedAt = now
				}

				genId := fmt.Sprintf("devseed-%s-%s-%d-%d-%s", owner, dayStr, chargeIdx, j, util.RandomTokenNoTs(6))

				resProviders = append(resProviders, key.Provider)
				resCreatedAt = append(resCreatedAt, createdAt)
				resProjects = append(resProjects, owner)
				resProducts = append(resProducts, picked.ProductId)
				resGenIds = append(resGenIds, genId)

				jobApps = append(jobApps, appName)
				jobTimeAllocs = append(jobTimeAllocs, timeAllocMillis)
				jobReplicas = append(jobReplicas, replicas)
				jobNames = append(jobNames, fmt.Sprintf("%s %s", appName, dayStr))
				jobStates = append(jobStates, state)
				jobStartedAt = append(jobStartedAt, startedAt)
				jobLastUpdate = append(jobLastUpdate, finishedAt)
			}
		}

		if len(resProviders) > 0 {
			if len(allocatedIds) < len(resProviders) {
				needed := len(resProviders) - len(allocatedIds)
				endId := resourceGlobals.IdAcc.Add(int64(needed))
				startId := endId - int64(needed) + 1
				for id := startId; id <= endId; id++ {
					allocatedIds = append(allocatedIds, id)
				}
			}
			resIds := allocatedIds[:len(resProviders)]

			db.Exec(
				tx,
				`
					with
						input_rows as (
							select
								ord,
								id,
								provider,
								created_at,
								project,
								product,
								gen_id,
								app_name,
								time_alloc,
								replicas,
								job_name,
								state,
								started_at,
								last_update
							from unnest(
								cast(:ids as bigint[]),
								cast(:providers as text[]),
								cast(:created_ats as timestamptz[]),
								cast(:projects as text[]),
								cast(:products as bigint[]),
								cast(:gen_ids as text[]),
								cast(:app_names as text[]),
								cast(:time_allocs as bigint[]),
								cast(:replicas as int[]),
								cast(:job_names as text[]),
								cast(:states as text[]),
								cast(:started_ats as timestamptz[]),
								cast(:last_updates as timestamptz[])
							) with ordinality as t(
								id,
								provider,
								created_at,
								project,
								product,
								gen_id,
								app_name,
								time_alloc,
								replicas,
								job_name,
								state,
								started_at,
								last_update,
								ord
							)
						),
						inserted_resources as (
							insert into provider.resource(
								id,
								type,
								provider,
								created_at,
								created_by,
								project,
								product,
								provider_generated_id,
								confirmed_by_provider,
								public_read
							)
							select
								ir.id,
								'job',
								pp.unique_name,
								ir.created_at,
								'_ucloud',
								ir.project,
								ir.product,
								ir.gen_id,
								true,
								false
							from
								input_rows ir
								left join provider.providers pp on pp.unique_name = ir.provider
							returning id
						)
					insert into app_orchestrator.jobs(
						application_name,
						application_version,
						time_allocation_millis,
						replicas,
						name,
						current_state,
						last_update,
						started_at,
						resource
					)
					select
						ir.app_name,
						'1.0',
						ir.time_alloc,
						ir.replicas,
						ir.job_name,
						ir.state,
						(ir.last_update at time zone 'utc'),
						(ir.started_at at time zone 'utc'),
						res.id
					from
						input_rows ir
						join inserted_resources res on res.id = ir.id
				`,
				db.Params{
					"ids":          resIds,
					"providers":    resProviders,
					"created_ats":  resCreatedAt,
					"projects":     resProjects,
					"products":     resProducts,
					"gen_ids":      resGenIds,
					"app_names":    jobApps,
					"time_allocs":  jobTimeAllocs,
					"replicas":     jobReplicas,
					"job_names":    jobNames,
					"states":       jobStates,
					"started_ats":  jobStartedAt,
					"last_updates": jobLastUpdate,
				},
			)
		}

		seedErr = tx.ConsumeError()
	})

	if seedErr != nil {
		log.Warn("Could not seed compute jobs for scenario: %s", seedErr.Error())
		return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "could not seed compute jobs")
	}

	return util.Empty{}, nil
}
