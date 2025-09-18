package reporting

import (
	"fmt"
	"slices"
	"time"
	"ucloud.dk/shared/pkg/util"
)

type DeicPerson struct {
	/*
	 * User must have a ORCID. This needs to be collected when loging in.
	 */
	Orcid util.Option[string] `json:"orcid"`

	/*
	 * Local id. Some unique id, uuid/guid
	 */
	LocalId string `json:"localId"`

	/*
	 * Each project that are assigned usage time have a generated project id. The format of the ID is GUID.
	 */
	DeicProjectId string `json:"deicProjectId"`

	/*
	 * Each HPC center has a unique ID. This is defined as a GUID
	 */
	HpcCenterId string `json:"hpcCenterId"`

	/*
	 * In case of sub centers they can use a sub id. This is defined as a GUID
	 */
	SubHPCCenterId string `json:"subHPCCenterId"`

	/*
	 * Each university is defined as a constant. New will be added if needed.
	 */
	UniversityId DeicUniversityId `json:"universityId"`

	/*
	 * In case of unknown, industry or other is used please specify in the IdExpanded field.
	 */
	IdExpanded util.Option[string] `json:"idExpanded"`

	/*
	 * Each access type is defined as a constand.
	 */
	AccessType DeicAccessType `json:"accessType"`

	/*
	 * Access start time in ISO 8601 format.
	 */
	AccessStartDate string `json:"accessStartDate"`

	/*
	 * Access end time in ISO 8601 format.
	 */
	AccessEndDate string `json:"accessEndDate"`

	/*
	 * Assigned CPU core time in hours
	 */
	CpuCoreTimeAssigned int64 `json:"cpuCoreTimeAssigned"`

	/*
	 * Used CPU core time in hours
	 */
	CpuCoreTimeUsed int64 `json:"cpuCoreTimeUsed"`

	/*
	 * Assigned GPU core time in hours
	 */
	GpuCoreTimeAssigned int64 `json:"gpuCoreTimeAssigned"`

	/*
	 * Used GPU core time in hours
	 */
	GpuCoreTimeUsed int64 `json:"gpuCoreTimeUsed"`

	/*
	 * Assigned storage space in MB
	 */
	StorageAssignedInMB int64 `json:"storageAssignedInMB"`

	/*
	 * Used storage space in MB
	 */
	StorageUsedInMB int64 `json:"storageUsedInMB"`
}

type DeicCenter struct {
	/*
	 * Each HPC center has a unique ID. This is defined as a GUID
	 */
	HpcCenterId string `json:"hpcCenterId"`

	/*
	 * In case of sub centers they can use a sub id. This is defined as a GUID
	 */
	SubHPCCenterId util.Option[string] `json:"subHPCCenterId"`

	/*
	 * Start time report periode in ISO 8601 format.
	 */
	StartPeriod string `json:"startPeriod"`

	/*
	 * End time report periode in ISO 8601 format.
	 */
	EndPeriod string `json:"endPeriod"`

	/*
	 * Max CPU core time in hours
	 */
	MaxCPUCoreTime int64 `json:"maxCPUCoreTime"`

	/*
	 * Used CPU core time in hours
	 */
	UsedCPUCoreTime int64 `json:"usedCPUCoreTime"`

	/*
	 * Max GPU core time in hours
	 */
	MaxGPUCoreTime int64 `json:"maxGPUCoreTime"`

	/*
	 * Used GPU core time in hours
	 */
	UsedGPUCoreTime int64 `json:"usedGPUCoreTime"`

	/*
	 * Storage space in MB for the period
	 */
	StorageUsedInMB int64 `json:"storageUsedInMB"`
}

type DeicUniversityId int

const (
	DeicUniversityUnknown DeicUniversityId = 0
	DeicUniversityKU      DeicUniversityId = 1
	DeicUniversityAU      DeicUniversityId = 2
	DeicUniversitySDU     DeicUniversityId = 3
	DeicUniversityDTU     DeicUniversityId = 4
	DeicUniversityAAU     DeicUniversityId = 5
	DeicUniversityRUC     DeicUniversityId = 6
	DeicUniversityITU     DeicUniversityId = 7
	DeicUniversityCBS     DeicUniversityId = 8
)

func deicUniversityIdFromOrgId(orgId string) DeicUniversityId {
	switch orgId {
	case "sdu.dk":
		return DeicUniversitySDU
	case "aau.dk":
		return DeicUniversityAAU
	case "au.dk":
		return DeicUniversityAU
	case "ku.dk":
		return DeicUniversityKU
	case "dtu.dk":
		return DeicUniversityDTU
	case "ruc.dk":
		return DeicUniversityRUC
	case "itu.dk":
		return DeicUniversityITU
	case "cbs.dk":
		return DeicUniversityCBS
	default:
		return DeicUniversityUnknown
	}
}

type DeicAccessType int

const (
	DeicAccessUnknown       DeicAccessType = 0
	DeicAccessLocal         DeicAccessType = 1
	DeicAccessNational      DeicAccessType = 2
	DeicAccessSandbox       DeicAccessType = 3
	DeicAccessInternational DeicAccessType = 4
)

type DeicReports struct {
	Start   time.Time
	End     time.Time
	Centers map[string]*DeicCenterReport
	Persons []DeicPerson
}

type DeicCenterReport struct {
	Period DeicCenter
	Daily  []DeicCenter
}

const CenterType1 = "f0679faa-242e-11eb-3aba-b187bcbee6d4"
const CenterType1Sdu = "0534ca5e-242f-11eb-2dca-2fe365de2d94"
const CenterType1Aau = "1003d37e-242f-11eb-186e-0722713fb0ad"

func providerToCenter(provider string) (string, string) {
	switch provider {
	case "ucloud":
		return CenterType1, CenterType1Sdu
	default:
		panic("unknown provider: " + provider)
	}
}

func centerTitle(center, subcenter string) string {
	if center == CenterType1 {
		if subcenter == CenterType1Sdu {
			return "DeiC Interactive HPC SDU"
		} else if subcenter == CenterType1Aau {
			return "DeiC Interactive HPC AAU"
		} else {
			return "DeiC Interactive HPC"
		}
	} else {
		return fmt.Sprintf("%s/%s", center, subcenter)
	}
}

func convertReports(start, end time.Time, reports []reportByGrantGiver) DeicReports {
	result := DeicReports{
		Start:   start,
		End:     end,
		Centers: map[string]*DeicCenterReport{},
	}

	centerCollapsed := map[string]*ServiceProviderReport{}
	centerDailyCollapsed := map[string]map[time.Time]*ServiceProviderReport{}

	for _, report := range reports {
		provider := report.CenterPeriod.Provider

		{
			current, ok := centerCollapsed[provider]
			if !ok {
				current = &ServiceProviderReport{
					Provider:  report.CenterPeriod.Provider,
					Start:     report.CenterPeriod.Start,
					End:       report.CenterPeriod.End,
					Resources: map[string]ResourceUseAndCapacity{},
				}
				centerCollapsed[provider] = current
			}

			for k, v := range report.CenterPeriod.Resources {
				current.Resources[k] = ResourceUseAndCapacity{
					Usage:    current.Resources[k].Usage + v.Usage,
					Capacity: current.Resources[k].Capacity + v.Capacity,
					Unit:     v.Unit,
				}
			}
		}

		for ts, dailyReport := range report.CenterDaily {
			currentMap, ok := centerDailyCollapsed[provider]
			if !ok {
				currentMap = map[time.Time]*ServiceProviderReport{}
				centerDailyCollapsed[provider] = currentMap
			}

			current, ok := currentMap[ts]
			if !ok {
				current = &ServiceProviderReport{
					Provider:  dailyReport.Provider,
					Start:     dailyReport.Start,
					End:       dailyReport.End,
					Resources: map[string]ResourceUseAndCapacity{},
				}
				currentMap[ts] = current
			}

			for k, v := range dailyReport.Resources {
				current.Resources[k] = ResourceUseAndCapacity{
					Usage:    current.Resources[k].Usage + v.Usage,
					Capacity: current.Resources[k].Capacity + v.Capacity,
					Unit:     v.Unit,
				}
			}
		}
	}

	for _, report := range reports {
		center, subCenter := providerToCenter(report.CenterPeriod.Provider)

		grantGiverInfo := grantGivers[report.GrantGiver]
		if grantGiverInfo.Deic.Access == DeicAccessUnknown {
			continue // skip reports that are not related to deic
		}

		for _, userReport := range report.PersonPeriod {
			cpu, gpu, gb := deicUsages(userReport.Resources)
			if cpu == 0 && gpu == 0 && gb == 0 {
				continue
			}

			p := DeicPerson{
				LocalId: fmt.Sprintf(
					"%s/%s",
					util.Sha256([]byte(userReport.Id)),
					userReport.GrantGiver,
				),
				DeicProjectId:       userReport.Project,
				HpcCenterId:         center,
				SubHPCCenterId:      subCenter,
				UniversityId:        grantGiverInfo.Deic.UniversityCode, // TODO Is this the uni ID of the project or the person?
				AccessType:          grantGiverInfo.Deic.Access,
				AccessStartDate:     "", // TODO
				AccessEndDate:       "", // TODO
				CpuCoreTimeAssigned: 0,  // TODO
				CpuCoreTimeUsed:     cpu,
				GpuCoreTimeAssigned: 0, // TODO
				GpuCoreTimeUsed:     gpu,
				StorageAssignedInMB: 0, // TODO
				StorageUsedInMB:     gb,
			}

			result.Persons = append(result.Persons, p)
		}
	}

	for providerId := range centerDailyCollapsed {
		daily := centerDailyCollapsed[providerId]
		center, subCenter := providerToCenter(providerId)

		var ts []time.Time
		for timestamp, _ := range daily {
			ts = append(ts, timestamp)
		}
		slices.SortFunc(ts, func(a, b time.Time) int {
			return a.Compare(b)
		})

		for _, timestamp := range ts {
			centerReport := daily[timestamp]
			cpu, gpu, gb := deicUsages(centerReport.Resources)

			deicCenter, ok := result.Centers[centerReport.Provider]
			if !ok {
				deicCenter = &DeicCenterReport{}
				result.Centers[centerReport.Provider] = deicCenter
			}

			deicCenter.Daily = append(deicCenter.Daily, DeicCenter{
				HpcCenterId:     center,
				SubHPCCenterId:  util.OptValue(subCenter),
				StartPeriod:     deicFormatTime(centerReport.Start),
				EndPeriod:       deicFormatTime(centerReport.End),
				MaxCPUCoreTime:  0, // TODO
				UsedCPUCoreTime: cpu,
				MaxGPUCoreTime:  0, // TODO
				UsedGPUCoreTime: gpu,
				StorageUsedInMB: gb,
			})
		}

		{
			deicCenter, ok := result.Centers[providerId]
			if !ok {
				deicCenter = &DeicCenterReport{}
				result.Centers[providerId] = deicCenter
			}

			report := centerCollapsed[providerId]
			cpu, gpu, gb := deicUsages(report.Resources)

			deicCenter.Period = DeicCenter{
				HpcCenterId:     center,
				SubHPCCenterId:  util.OptValue(subCenter),
				StartPeriod:     deicFormatTime(report.Start),
				EndPeriod:       deicFormatTime(report.End),
				MaxCPUCoreTime:  0, // TODO
				UsedCPUCoreTime: cpu,
				MaxGPUCoreTime:  0, // TODO
				UsedGPUCoreTime: gpu,
				StorageUsedInMB: gb,
			}
		}
	}

	return result
}

func deicFormatTime(timestamp time.Time) string {
	return timestamp.UTC().Format("2006-01-02T15:04:05Z07:00")
}

func deicUsages(resources map[string]ResourceUseAndCapacity) (int64, int64, int64) {
	coreHoursUsed := float64(0)
	gpuHoursUsed := float64(0)
	gbUsed := float64(0)

	for _, usage := range resources {
		if usage.Unit == gpuHours {
			gpuHoursUsed += usage.Usage
		} else if usage.Unit == coreHours {
			coreHoursUsed += usage.Usage
		} else if usage.Unit == storage {
			gbUsed += usage.Usage
		} else {
			panic("unknown unit: " + usage.Unit)
		}
	}

	return int64(coreHoursUsed), int64(gpuHoursUsed), int64(gbUsed * 1000.0)
}
