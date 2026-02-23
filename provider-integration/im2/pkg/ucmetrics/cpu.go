package ucmetrics

import (
	"bufio"
	"fmt"
	"os"
	"runtime"
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
	useSystemCPU     bool
	readUsageFn      func() (uint64, error)
	readCpuTimesFn   func() (uint64, uint64, error)
	initialUsage     uint64
	initialTotal     uint64
	cpuCount         float64
	ts               time.Time
}

func CpuSampleStart() (CpuSample, error) {
	result := CpuSample{}
	result.cgroupV2 = isCgroupV2()
	usingCgroup := false

	if result.cgroupV2 {
		cpuCount := calculateCGroupV2CpuCount()
		if cpuCount > 0 {
			result.readUsageFn = readCgroupV2CPUUsage
			result.conversionFactor = 1
			result.cpuCount = cpuCount
			usingCgroup = true
		}
	} else {
		cpuCount := calculateCGroupV1CpuCount()
		if cpuCount > 0 {
			result.readUsageFn = readCgroupV1CPUUsage
			result.conversionFactor = 1000
			result.cpuCount = cpuCount
			usingCgroup = true
		}
	}

	if !usingCgroup {
		enableMachineCpuSampling(&result)
	}

	result.ts = time.Now()
	var initialUsage uint64
	var err error
	if result.useSystemCPU {
		var initialTotal uint64
		initialUsage, initialTotal, err = result.readCpuTimesFn()
		result.initialTotal = initialTotal
	} else {
		initialUsage, err = result.readUsageFn()
		if err != nil && usingCgroup {
			enableMachineCpuSampling(&result)
			initialUsage, result.initialTotal, err = result.readCpuTimesFn()
		}
	}
	if err != nil {
		return CpuSample{}, err
	}

	result.initialUsage = initialUsage
	return result, nil
}

func (s *CpuSample) End() (CpuStats, error) {
	if s.useSystemCPU {
		endActive, endTotal, err := s.readCpuTimesFn()
		if err != nil {
			return CpuStats{}, err
		}

		totalDelta := endTotal - s.initialTotal
		if totalDelta == 0 || s.cpuCount == 0 {
			return CpuStats{}, fmt.Errorf("invalid CPU time values")
		}

		activeDelta := endActive - s.initialUsage
		if activeDelta > totalDelta {
			activeDelta = totalDelta
		}

		cpuUsage := (float64(activeDelta) / float64(totalDelta)) * s.cpuCount * 100.0
		return CpuStats{Usage: cpuUsage, Limit: s.cpuCount * 100}, nil
	}

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

func enableMachineCpuSampling(sample *CpuSample) {
	sample.useSystemCPU = true
	sample.readCpuTimesFn = readSystemCPUTime
	sample.conversionFactor = 1
	sample.cpuCount = float64(runtime.NumCPU())
}

func readSystemCPUTime() (uint64, uint64, error) {
	file, err := os.Open("/proc/stat")
	if err != nil {
		return 0, 0, err
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	if !scanner.Scan() {
		return 0, 0, fmt.Errorf("could not read /proc/stat")
	}

	fields := strings.Fields(scanner.Text())
	if len(fields) < 5 || fields[0] != "cpu" {
		return 0, 0, fmt.Errorf("invalid cpu line in /proc/stat")
	}

	values := make([]uint64, 0, len(fields)-1)
	for _, field := range fields[1:] {
		value, parseErr := strconv.ParseUint(field, 10, 64)
		if parseErr != nil {
			return 0, 0, parseErr
		}
		values = append(values, value)
	}

	total := uint64(0)
	for _, value := range values {
		total += value
	}

	idle := values[3]
	if len(values) > 4 {
		idle += values[4]
	}

	if err := scanner.Err(); err != nil {
		return 0, 0, err
	}

	return total - idle, total, nil
}

func calculateCGroupV1CpuCount() float64 {
	quotaRaw := readFirstLine("/sys/fs/cgroup/cpu,cpuacct/cpu.cfs_quota_us")
	periodRaw := readFirstLine("/sys/fs/cgroup/cpu,cpuacct/cpu.cfs_period_us")

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
