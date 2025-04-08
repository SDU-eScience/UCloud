package ucmetrics

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/shared/pkg/util"
)

// Paths for cgroup v1 and v2 files
const (
	cgroupV2CPUStat  = "/sys/fs/cgroup/cpu.stat"
	cgroupV1CPUUsage = "/sys/fs/cgroup/cpu/cpuacct.usage"
)

// Reads CPU usage from cgroups v2 (in microseconds)
func readCgroupV2CPUUsage() (uint64, error) {
	file, err := os.Open(cgroupV2CPUStat)
	if err != nil {
		return 0, err
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) == 2 && fields[0] == "usage_usec" {
			return strconv.ParseUint(fields[1], 10, 64)
		}
	}
	return 0, fmt.Errorf("usage_usec not found in %s", cgroupV2CPUStat)
}

// Reads CPU usage from cgroups v1 (in nanoseconds)
func readCgroupV1CPUUsage() (uint64, error) {
	file, err := os.Open(cgroupV1CPUUsage)
	if err != nil {
		return 0, err
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	if scanner.Scan() {
		return strconv.ParseUint(scanner.Text(), 10, 64)
	}
	return 0, fmt.Errorf("could not read %s", cgroupV1CPUUsage)
}

type CpuSample struct {
	conversionFactor uint64
	cgroupV2         bool
	readUsageFn      func() (uint64, error)
	initialUsage     uint64
	cpuCount         float64
	ts               time.Time
}

func CpuSampleStart() (CpuSample, error) {
	result := CpuSample{}
	result.cgroupV2 = isCgroupV2()

	if result.cgroupV2 {
		result.readUsageFn = readCgroupV2CPUUsage
		result.conversionFactor = 1 // microseconds (µs) to µs, no conversion needed
		result.cpuCount = calculateCGroupV2CpuCount()
	} else {
		result.readUsageFn = readCgroupV1CPUUsage
		result.conversionFactor = 1000 // nanoseconds (ns) to µs (divide by 1000)
		result.cpuCount = calculateCGroupV1CpuCount()
	}

	result.ts = time.Now()
	initialUsage, err := result.readUsageFn()
	if err != nil {
		return CpuSample{}, err
	}

	result.initialUsage = initialUsage
	return result, nil
}

func (s *CpuSample) End() (CpuStats, error) {
	endUsage, err := s.readUsageFn()
	if err != nil {
		return CpuStats{}, err
	}
	now := time.Now()
	interval := now.Sub(s.ts)
	totalDelta := int64(float64(interval.Microseconds()) * s.cpuCount)

	// Compute differences
	cgroupDelta := (endUsage - s.initialUsage) / s.conversionFactor // Convert ns → µs if cgroup v1

	// Avoid division by zero
	if totalDelta == 0 || s.cpuCount == 0 {
		return CpuStats{}, fmt.Errorf("invalid CPU time values")
	}

	// CPU usage as a percentage (100% per core)
	cpuUsage := (float64(cgroupDelta) / float64(totalDelta)) * float64(s.cpuCount) * 100.0

	return CpuStats{Usage: cpuUsage, Limit: s.cpuCount * 100}, nil
}

func calculateCGroupV1CpuCount() float64 {
	quotaRaw := readFirstLine("/sys/fs/cgroup/cpu,cpuacct/cpu.cfs_quota_us")
	periodRaw := readFirstLine("/sys/fs/cgroup/cpu,cpuacct/cpu.cfg_period_us")

	quota, _ := strconv.ParseUint(quotaRaw, 10, 64)
	period, _ := strconv.ParseUint(periodRaw, 10, 64)

	if period == 0 {
		return 0
	}

	return float64(quota) / float64(period)
}

func calculateCGroupV2CpuCount() float64 {
	line := readFirstLine("/sys/fs/cgroup/cpu.max")
	fields := strings.Fields(line)
	if len(fields) != 2 {
		return 0
	}

	quotaRaw := fields[0]
	periodRaw := fields[1]

	quota, _ := strconv.ParseUint(quotaRaw, 10, 64)
	period, _ := strconv.ParseUint(periodRaw, 10, 64)

	if period == 0 {
		return 0
	}

	return float64(quota) / float64(period)
}
