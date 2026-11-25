package inference

import (
	"hash/fnv"
	"math"
	"math/rand/v2"
	"net/http"
	"strings"
	"time"

	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func inferenceDevSeedModels() []InferenceModel {
	basePath := strings.TrimSpace(shared.ServiceConfig.Compute.Inference.BackendServer)
	if basePath == "" {
		basePath = "http://localai:8080/v1"
	}
	defaultSettings := InferenceChatSettings{
		Temperature:         0.7,
		TopP:                0.9,
		MaxCompletionTokens: 8192,
	}
	return []InferenceModel{
		{
			Name:         "zai-org/GLM-5.3",
			Title:        "GLM-5.3",
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: 1_163_000,
				Input:       5_815_000,
				Output:      17_362_000,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "zai-org/GLM-5.3",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:         "zai-org/GLM-5.3-Flash",
			Title:        "GLM-5.3 Flash",
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: 151_000,
				Input:       753_000,
				Output:      2_832_000,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "zai-org/GLM-5.3-Flash",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:         "deepseek-ai/DeepSeek-V4.1-Flash",
			Title:        "DeepSeek V4.1 Flash",
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: 141_120,
				Input:       705_600,
				Output:      2_116_800,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "deepseek-ai/DeepSeek-V4.1-Flash",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:         "qwen/Qwen3-Coder-480B",
			Title:        "Qwen3 Coder 480B",
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: 450_000,
				Input:       2_250_000,
				Output:      9_000_000,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "qwen/Qwen3-Coder-480B",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:         "meta-llama/Llama-4-Maverick",
			Title:        "Llama 4 Maverick",
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: 300_000,
				Input:       1_500_000,
				Output:      6_000_000,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "meta-llama/Llama-4-Maverick",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:              "black-forest-labs/FLUX.1-pro",
			Title:             "FLUX.1 Pro",
			Capabilities:      []InferenceCapability{InferenceTextToImage},
			PricePerMegaPixel: 45_000,
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "black-forest-labs/FLUX.1-pro",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:                "openai/whisper-large-v3",
			Title:               "Whisper Large v3",
			Capabilities:        []InferenceCapability{InferenceSpeechToText},
			PricePerAudioMinute: 60_000,
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "openai/whisper-large-v3",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
		{
			Name:         "wan-ai/Wan2.2-T2V",
			Title:        "Wan 2.2 Video",
			Capabilities: []InferenceCapability{InferenceTextToVideo},
			PricePerVideoSecond: map[InferenceVideoTier]int64{
				InferenceVideoTier720p:  500_000,
				InferenceVideoTier1080p: 900_000,
			},
			VideoResolutions: []InferenceVideoResolution{
				{Width: 1280, Height: 720, Tier: InferenceVideoTier720p},
				{Width: 1920, Height: 1080, Tier: InferenceVideoTier1080p},
			},
			DefaultVideoTier: InferenceVideoTier720p,
			Endpoint: InferenceEndpoint{
				BasePath:         basePath,
				BackendModelName: "wan-ai/Wan2.2-T2V",
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: []string{},
			},
			ChatSettings: defaultSettings,
		},
	}
}

func inferenceEnsureDevSeedModels() []InferenceModel {
	seedModels := inferenceDevSeedModels()

	modelGlobals.Mu.Lock()
	defer modelGlobals.Mu.Unlock()

	if modelGlobals.Models == nil {
		modelGlobals.Models = map[string]InferenceModel{}
	}

	var toInsert []InferenceModel
	resolved := make([]InferenceModel, 0, len(seedModels))
	for _, m := range seedModels {
		if current, ok := modelGlobals.Models[m.Name]; ok {
			resolved = append(resolved, inferenceModelClone(current))
			continue
		}
		normalized := inferenceModelNormalize(m)
		if normalized.ReasoningEfforts == nil {
			normalized.ReasoningEfforts = []InferenceModelOption{}
		}
		if normalized.DefaultFor == nil {
			normalized.DefaultFor = []InferenceCapability{}
		}
		toInsert = append(toInsert, normalized)
		resolved = append(resolved, normalized)
	}

	if len(toInsert) > 0 {
		db.NewTx0(func(tx *db.Transaction) {
			for _, m := range toInsert {
				inferenceModelUpsertTx(tx, m)
			}
		})
		for _, m := range toInsert {
			modelGlobals.Models[m.Name] = inferenceModelClone(m)
		}
	}

	return resolved
}

func inferenceSeedRng(parts ...string) *rand.Rand {
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

func inferenceHandleUsageBreakdownSeed(
	info rpc.RequestInfo,
	request orcapi.InferenceUsageBreakdownSeedProviderRequest,
) (util.Empty, *util.HttpError) {
	_ = info
	if !util.DevelopmentModeEnabled() {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "development mode required")
	}
	if len(request.Charges) == 0 {
		return util.Empty{}, nil
	}

	seedModels := inferenceEnsureDevSeedModels()
	var textModels []InferenceModel
	var imageModel *InferenceModel
	var audioModel *InferenceModel
	var videoModel *InferenceModel
	for i := range seedModels {
		m := &seedModels[i]
		hasTextPrice := m.PricePerMillion.Input > 0 && m.PricePerMillion.Output > 0
		if hasTextPrice {
			textModels = append(textModels, *m)
		}
		if m.PricePerMegaPixel > 0 && imageModel == nil {
			imageModel = m
		}
		if m.PricePerAudioMinute > 0 && audioModel == nil {
			audioModel = m
		}
		if len(m.PricePerVideoSecond) > 0 && videoModel == nil {
			videoModel = m
		}
	}
	if len(textModels) == 0 {
		return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "no priced text inference models available for seeding")
	}

	owners := make([]string, 0, len(request.Charges)*4)
	models := make([]string, 0, len(request.Charges)*4)
	days := make([]string, 0, len(request.Charges)*4)
	cachedTokens := make([]int64, 0, len(request.Charges)*4)
	inputTokens := make([]int64, 0, len(request.Charges)*4)
	outputTokens := make([]int64, 0, len(request.Charges)*4)
	imageCounts := make([]int64, 0, len(request.Charges)*4)
	imagePixels := make([]int64, 0, len(request.Charges)*4)
	audioMs := make([]int64, 0, len(request.Charges)*4)
	videoSecs := make([]int64, 0, len(request.Charges)*4)

	ownerProfiles := map[string][]float64{}
	getOwnerProfile := func(owner string) []float64 {
		if prof, ok := ownerProfiles[owner]; ok {
			return prof
		}
		ownerRng := inferenceSeedRng("owner-profile", owner)
		weights := make([]float64, len(textModels))
		primary := ownerRng.IntN(len(textModels))
		secondary := (primary + 1 + ownerRng.IntN(max(1, len(textModels)-1))) % len(textModels)
		for i := range weights {
			w := 0.04 + ownerRng.Float64()*0.10
			if i == primary {
				w += 0.45 + ownerRng.Float64()*0.25
			} else if i == secondary {
				w += 0.18 + ownerRng.Float64()*0.15
			}
			weights[i] = w
		}
		ownerProfiles[owner] = weights
		return weights
	}

	for _, charge := range request.Charges {
		owner := strings.TrimSpace(charge.Owner)
		day := strings.TrimSpace(charge.Day)
		if owner == "" || day == "" || charge.MicroCredits <= 0 {
			continue
		}
		_, dayErr := time.Parse("2006-01-02", day)
		if dayErr != nil {
			continue
		}

		rng := inferenceSeedRng("charge", owner, day)
		remainingBudget := float64(charge.MicroCredits)

		if imageModel != nil && remainingBudget >= 90_000 && rng.Float64() < 0.25 {
			share := remainingBudget * (0.04 + rng.Float64()*0.07)
			megapixels := share / float64(imageModel.PricePerMegaPixel)
			count := int64(math.Max(1, math.Round(megapixels)))
			pixels := count * 1_048_576
			actualCost := float64(pixels) * float64(imageModel.PricePerMegaPixel) / float64(InferencePriceScale)
			if actualCost < remainingBudget*0.5 {
				remainingBudget -= actualCost
				owners = append(owners, owner)
				models = append(models, imageModel.Name)
				days = append(days, day)
				cachedTokens = append(cachedTokens, 0)
				inputTokens = append(inputTokens, 0)
				outputTokens = append(outputTokens, 0)
				imageCounts = append(imageCounts, count)
				imagePixels = append(imagePixels, pixels)
				audioMs = append(audioMs, 0)
				videoSecs = append(videoSecs, 0)
			}
		}

		if audioModel != nil && remainingBudget >= 120_000 && rng.Float64() < 0.20 {
			share := remainingBudget * (0.03 + rng.Float64()*0.06)
			ms := int64(math.Max(5_000, math.Round(share*60000.0/float64(audioModel.PricePerAudioMinute))))
			actualCost := float64(ms) * float64(audioModel.PricePerAudioMinute) / 60000.0
			if actualCost < remainingBudget*0.4 {
				remainingBudget -= actualCost
				owners = append(owners, owner)
				models = append(models, audioModel.Name)
				days = append(days, day)
				cachedTokens = append(cachedTokens, 0)
				inputTokens = append(inputTokens, 0)
				outputTokens = append(outputTokens, 0)
				imageCounts = append(imageCounts, 0)
				imagePixels = append(imagePixels, 0)
				audioMs = append(audioMs, ms)
				videoSecs = append(videoSecs, 0)
			}
		}

		if videoModel != nil && remainingBudget >= 1_500_000 && rng.Float64() < 0.14 {
			videoPrice := videoModel.PricePerVideoSecond[videoModel.DefaultVideoTier]
			if videoPrice > 0 {
				share := remainingBudget * (0.06 + rng.Float64()*0.09)
				secs := int64(math.Max(1, math.Round(share/float64(videoPrice))))
				actualCost := float64(secs) * float64(videoPrice)
				if actualCost < remainingBudget*0.4 {
					remainingBudget -= actualCost
					owners = append(owners, owner)
					models = append(models, videoModel.Name)
					days = append(days, day)
					cachedTokens = append(cachedTokens, 0)
					inputTokens = append(inputTokens, 0)
					outputTokens = append(outputTokens, 0)
					imageCounts = append(imageCounts, 0)
					imagePixels = append(imagePixels, 0)
					audioMs = append(audioMs, 0)
					videoSecs = append(videoSecs, secs)
				}
			}
		}

		baseWeights := getOwnerProfile(owner)
		dayWeights := make([]float64, len(textModels))
		weightSum := 0.0
		for i := range textModels {
			jitter := 0.55 + rng.Float64()*0.90
			dayWeights[i] = baseWeights[i] * jitter
			weightSum += dayWeights[i]
		}
		if weightSum <= 0 {
			continue
		}

		for i, m := range textModels {
			modelBudget := remainingBudget * (dayWeights[i] / weightSum)
			if modelBudget < 100 {
				continue
			}

			cachedShare := 0.08 + rng.Float64()*0.10
			inputShare := 0.30 + rng.Float64()*0.16
			outputShare := 1.0 - cachedShare - inputShare

			cTok := int64(0)
			if m.PricePerMillion.CachedInput > 0 {
				cTok = int64(math.Round((modelBudget * cachedShare) * float64(InferencePriceScale) / float64(m.PricePerMillion.CachedInput)))
			} else {
				inputShare += cachedShare
			}
			iTok := int64(math.Round((modelBudget * inputShare) * float64(InferencePriceScale) / float64(m.PricePerMillion.Input)))
			oTok := int64(math.Round((modelBudget * outputShare) * float64(InferencePriceScale) / float64(m.PricePerMillion.Output)))

			if cTok <= 0 && iTok <= 0 && oTok <= 0 {
				continue
			}

			owners = append(owners, owner)
			models = append(models, m.Name)
			days = append(days, day)
			cachedTokens = append(cachedTokens, cTok)
			inputTokens = append(inputTokens, iTok)
			outputTokens = append(outputTokens, oTok)
			imageCounts = append(imageCounts, 0)
			imagePixels = append(imagePixels, 0)
			audioMs = append(audioMs, 0)
			videoSecs = append(videoSecs, 0)
		}
	}

	if len(owners) == 0 {
		return util.Empty{}, nil
	}

	var seedDbErr error
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				with seeded as (
					select
						owner,
						model,
						cast(day as date) as usage_day,
						cast(sum(cached_input_tokens) as bigint) as cached_input_tokens,
						cast(sum(input_tokens) as bigint) as input_tokens,
						cast(sum(output_tokens) as bigint) as output_tokens,
						cast(sum(image_count) as bigint) as image_count,
						cast(sum(image_pixels) as bigint) as image_pixels,
						cast(sum(audio_milliseconds) as bigint) as audio_milliseconds,
						cast(sum(video_seconds) as bigint) as video_seconds
					from unnest(
						cast(:owners as text[]),
						cast(:models as text[]),
						cast(:days as text[]),
						cast(:cached_input_tokens as bigint[]),
						cast(:input_tokens as bigint[]),
						cast(:output_tokens as bigint[]),
						cast(:image_counts as bigint[]),
						cast(:image_pixels as bigint[]),
						cast(:audio_milliseconds as bigint[]),
						cast(:video_seconds as bigint[])
					) as t(
						owner,
						model,
						day,
						cached_input_tokens,
						input_tokens,
						output_tokens,
						image_count,
						image_pixels,
						audio_milliseconds,
						video_seconds
					)
					group by
						owner,
						model,
						cast(day as date)
				)
				insert into inference_usage_by_model(
					owner,
					username,
					model,
					usage_day,
					cached_input_tokens,
					input_tokens,
					output_tokens,
					image_count,
					image_pixels,
					audio_milliseconds,
					video_seconds
				)
				select
					owner,
					'_ucloud',
					model,
					usage_day,
					cached_input_tokens,
					input_tokens,
					output_tokens,
					image_count,
					image_pixels,
					audio_milliseconds,
					video_seconds
				from
					seeded
				on conflict (owner, username, model, usage_day) do update set
					cached_input_tokens = inference_usage_by_model.cached_input_tokens + excluded.cached_input_tokens,
					input_tokens = inference_usage_by_model.input_tokens + excluded.input_tokens,
					output_tokens = inference_usage_by_model.output_tokens + excluded.output_tokens,
					image_count = inference_usage_by_model.image_count + excluded.image_count,
					image_pixels = inference_usage_by_model.image_pixels + excluded.image_pixels,
					audio_milliseconds = inference_usage_by_model.audio_milliseconds + excluded.audio_milliseconds,
					video_seconds = inference_usage_by_model.video_seconds + excluded.video_seconds,
					updated_at = now()
			`,
			db.Params{
				"owners":              owners,
				"models":              models,
				"days":                days,
				"cached_input_tokens": cachedTokens,
				"input_tokens":        inputTokens,
				"output_tokens":       outputTokens,
				"image_counts":        imageCounts,
				"image_pixels":        imagePixels,
				"audio_milliseconds":  audioMs,
				"video_seconds":       videoSecs,
			},
		)
		seedDbErr = tx.ConsumeError()
	})

	if seedDbErr != nil {
		log.Warn("Could not seed inference_usage_by_model: %s", seedDbErr.Error())
		return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "could not seed inference usage")
	}

	return util.Empty{}, nil
}
