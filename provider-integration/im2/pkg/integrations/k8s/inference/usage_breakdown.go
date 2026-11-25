package inference

import (
	"math"
	"net/http"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func inferenceHandleUsageBreakdown(
	info rpc.RequestInfo,
	request orcapi.InferenceUsageBreakdownProviderRequest,
) (orcapi.InferenceUsageBreakdownResponse, *util.HttpError) {
	_ = info

	startDate := strings.TrimSpace(request.StartDate)
	endDate := strings.TrimSpace(request.EndDate)
	if startDate == "" || endDate == "" {
		return orcapi.InferenceUsageBreakdownResponse{}, util.HttpErr(http.StatusBadRequest, "startDate and endDate are required")
	}
	parsedStart, startErr := time.Parse("2006-01-02", startDate)
	if startErr != nil {
		return orcapi.InferenceUsageBreakdownResponse{}, util.HttpErr(http.StatusBadRequest, "invalid startDate")
	}
	parsedEnd, endErr := time.Parse("2006-01-02", endDate)
	if endErr != nil {
		return orcapi.InferenceUsageBreakdownResponse{}, util.HttpErr(http.StatusBadRequest, "invalid endDate")
	}
	if parsedEnd.Before(parsedStart) {
		return orcapi.InferenceUsageBreakdownResponse{
			Rows: []orcapi.InferenceUsageBreakdownDailyRow{},
		}, nil
	}

	exclusiveSet := map[string]util.Empty{}
	exclusiveOwners := make([]string, 0, len(request.ExclusiveOwners))
	for _, owner := range request.ExclusiveOwners {
		trimmed := strings.TrimSpace(owner)
		if trimmed == "" {
			continue
		}
		if _, exists := exclusiveSet[trimmed]; exists {
			continue
		}
		exclusiveSet[trimmed] = util.Empty{}
		exclusiveOwners = append(exclusiveOwners, trimmed)
	}

	weightOwners := make([]string, 0, len(request.DailyWeights))
	weightDays := make([]string, 0, len(request.DailyWeights))
	weightValues := make([]float64, 0, len(request.DailyWeights))
	hasFractionalWeights := false
	for _, entry := range request.DailyWeights {
		owner := strings.TrimSpace(entry.Owner)
		day := strings.TrimSpace(entry.Day)
		if owner == "" || day == "" || entry.Weight <= 0 {
			continue
		}
		if _, isExclusive := exclusiveSet[owner]; isExclusive {
			continue
		}
		_, dayErr := time.Parse("2006-01-02", day)
		if dayErr != nil {
			continue
		}
		w := entry.Weight
		if w > 1.0 {
			w = 1.0
		}
		if w < 1.0 {
			hasFractionalWeights = true
		}
		weightOwners = append(weightOwners, owner)
		weightDays = append(weightDays, day)
		weightValues = append(weightValues, w)
	}

	if len(exclusiveOwners) == 0 && len(weightOwners) == 0 {
		return orcapi.InferenceUsageBreakdownResponse{
			Rows: []orcapi.InferenceUsageBreakdownDailyRow{},
		}, nil
	}

	type aggregatedUsageRow struct {
		Day               string
		Model             string
		CachedInputTokens float64
		InputTokens       float64
		OutputTokens      float64
		ImageCount        float64
		ImagePixels       float64
		AudioMilliseconds float64
		VideoSeconds      float64
	}

	var rawRows []aggregatedUsageRow
	coFundedCount := 0

	db.NewTx0(func(tx *db.Transaction) {
		rawRows = db.Select[aggregatedUsageRow](
			tx,
			`
				with
					exclusive_owners as (
						select distinct owner
						from unnest(cast(:exclusive_owners as text[])) as t(owner)
					),
					daily_weights as (
						select
							owner,
							cast(day as date) as usage_day,
							weight
						from unnest(
							cast(:weight_owners as text[]),
							cast(:weight_days as text[]),
							cast(:weight_values as double precision[])
						) as t(owner, day, weight)
					),
					weighted_usage as (
						select
							u.usage_day,
							u.model,
							cast(u.cached_input_tokens as double precision) as cached_input_tokens,
							cast(u.input_tokens as double precision) as input_tokens,
							cast(u.output_tokens as double precision) as output_tokens,
							cast(u.image_count as double precision) as image_count,
							cast(u.image_pixels as double precision) as image_pixels,
							cast(u.audio_milliseconds as double precision) as audio_milliseconds,
							cast(u.video_seconds as double precision) as video_seconds
						from
							inference_usage_by_model u
							join exclusive_owners eo on u.owner = eo.owner
						where
							u.usage_day >= cast(:start_date as date)
							and u.usage_day <= cast(:end_date as date)
						union all
						select
							u.usage_day,
							u.model,
							cast(u.cached_input_tokens as double precision) * dw.weight as cached_input_tokens,
							cast(u.input_tokens as double precision) * dw.weight as input_tokens,
							cast(u.output_tokens as double precision) * dw.weight as output_tokens,
							cast(u.image_count as double precision) * dw.weight as image_count,
							cast(u.image_pixels as double precision) * dw.weight as image_pixels,
							cast(u.audio_milliseconds as double precision) * dw.weight as audio_milliseconds,
							cast(u.video_seconds as double precision) * dw.weight as video_seconds
						from
							inference_usage_by_model u
							join daily_weights dw on
								u.owner = dw.owner
								and u.usage_day = dw.usage_day
						where
							u.usage_day >= cast(:start_date as date)
							and u.usage_day <= cast(:end_date as date)
					)
				select
					to_char(usage_day, 'YYYY-MM-DD') as day,
					model,
					sum(cached_input_tokens) as cached_input_tokens,
					sum(input_tokens) as input_tokens,
					sum(output_tokens) as output_tokens,
					sum(image_count) as image_count,
					sum(image_pixels) as image_pixels,
					sum(audio_milliseconds) as audio_milliseconds,
					sum(video_seconds) as video_seconds
				from
					weighted_usage
				group by
					usage_day,
					model
				order by
					usage_day,
					model
			`,
			db.Params{
				"start_date":       startDate,
				"end_date":         endDate,
				"exclusive_owners": exclusiveOwners,
				"weight_owners":    weightOwners,
				"weight_days":      weightDays,
				"weight_values":    weightValues,
			},
		)

		if hasFractionalWeights {
			countRow, ok := db.Get[struct {
				Count int
			}](
				tx,
				`
					with
						daily_weights as (
							select
								owner,
								cast(day as date) as usage_day,
								weight
							from unnest(
								cast(:weight_owners as text[]),
								cast(:weight_days as text[]),
								cast(:weight_values as double precision[])
							) as t(owner, day, weight)
						)
					select
						count(distinct u.owner) as count
					from
						inference_usage_by_model u
						join daily_weights dw on
							u.owner = dw.owner
							and u.usage_day = dw.usage_day
					where
						dw.weight < 1.0
						and u.usage_day >= cast(:start_date as date)
						and u.usage_day <= cast(:end_date as date)
						and (
							u.cached_input_tokens > 0
							or u.input_tokens > 0
							or u.output_tokens > 0
							or u.image_count > 0
							or u.image_pixels > 0
							or u.audio_milliseconds > 0
							or u.video_seconds > 0
						)
				`,
				db.Params{
					"start_date":    startDate,
					"end_date":      endDate,
					"weight_owners": weightOwners,
					"weight_days":   weightDays,
					"weight_values": weightValues,
				},
			)
			if ok {
				coFundedCount = countRow.Count
			}
		}
	})

	catalogModels := InferenceModelList()
	modelsByName := make(map[string]InferenceModel, len(catalogModels))
	for _, m := range catalogModels {
		modelsByName[m.Name] = m
	}

	resultRows := make([]orcapi.InferenceUsageBreakdownDailyRow, 0, len(rawRows))
	for _, raw := range rawRows {
		cachedTokens := int64(math.Round(raw.CachedInputTokens))
		inputTokens := int64(math.Round(raw.InputTokens))
		outputTokens := int64(math.Round(raw.OutputTokens))
		imageCount := int64(math.Round(raw.ImageCount))
		imagePixels := int64(math.Round(raw.ImagePixels))
		audioMs := int64(math.Round(raw.AudioMilliseconds))
		videoSec := int64(math.Round(raw.VideoSeconds))

		hasAnyUsage := cachedTokens > 0 ||
			inputTokens > 0 ||
			outputTokens > 0 ||
			imageCount > 0 ||
			imagePixels > 0 ||
			audioMs > 0 ||
			videoSec > 0
		if !hasAnyUsage {
			continue
		}

		outRow := orcapi.InferenceUsageBreakdownDailyRow{
			Day:               raw.Day,
			Model:             raw.Model,
			ModelTitle:        raw.Model,
			CachedInputTokens: cachedTokens,
			InputTokens:       inputTokens,
			OutputTokens:      outputTokens,
			ImageCount:        imageCount,
			ImagePixels:       imagePixels,
			AudioMilliseconds: audioMs,
			VideoSeconds:      videoSec,
		}

		model, hasModel := modelsByName[raw.Model]
		if hasModel {
			if model.Title != "" {
				outRow.ModelTitle = model.Title
			}
			outRow.ModelPriced = true
			outRow.EstimatedCachedInputCost = int64(math.Round(
				raw.CachedInputTokens * float64(model.PricePerMillion.CachedInput) / float64(InferencePriceScale),
			))
			outRow.EstimatedInputCost = int64(math.Round(
				raw.InputTokens * float64(model.PricePerMillion.Input) / float64(InferencePriceScale),
			))
			outRow.EstimatedOutputCost = int64(math.Round(
				raw.OutputTokens * float64(model.PricePerMillion.Output) / float64(InferencePriceScale),
			))
			outRow.EstimatedImageCost = int64(math.Round(
				raw.ImagePixels * float64(model.PricePerMegaPixel) / float64(InferencePriceScale),
			))
			outRow.EstimatedAudioCost = int64(math.Round(
				raw.AudioMilliseconds * float64(model.PricePerAudioMinute) / 60000.0,
			))

			videoPrice := int64(0)
			if model.DefaultVideoTier != "" {
				videoPrice = model.PricePerVideoSecond[model.DefaultVideoTier]
			}
			if videoPrice <= 0 {
				for _, tier := range inferenceVideoTierOrder {
					tierPrice := model.PricePerVideoSecond[tier]
					if tierPrice > 0 {
						videoPrice = tierPrice
						break
					}
				}
			}
			if raw.VideoSeconds > 0 && videoPrice > 0 {
				outRow.EstimatedVideoCost = int64(math.Round(raw.VideoSeconds * float64(videoPrice)))
				if len(model.PricePerVideoSecond) > 1 {
					outRow.VideoUsedDefaultTierPricing = true
				}
			}
		}

		resultRows = append(resultRows, outRow)
	}

	return orcapi.InferenceUsageBreakdownResponse{
		Rows:                   resultRows,
		CoFundedWorkspaceCount: coFundedCount,
	}, nil
}
