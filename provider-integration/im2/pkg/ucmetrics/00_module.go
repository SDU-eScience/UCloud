package ucmetrics

import (
	"bufio"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
	"sync"
	"ucloud.dk/shared/pkg/util"
)

func ReadMemoryUsage() uint64 {
	if isMemoryRestrictedByCgroup() {
		line := readFirstLine("/sys/fs/cgroup/memory.current")
		if line == "" {
			line = readFirstLine("/sys/fs/cgroup/memory/memory.usage_in_bytes")
		}

		memoryUsed, err := strconv.ParseUint(line, 10, 64)
		if err == nil {
			return memoryUsed
		}
	}

	return readSystemMemoryUsage()
}

func ReadMemoryLimit() uint64 {
	if limit, ok := readCgroupMemoryLimitIfRestricted(); ok {
		return limit
	}

	return readSystemMemoryLimit()
}

type NetworkUsage struct {
	Read  uint64
	Write uint64
}

var interfaceNames []string
var interfaceNamesOnce sync.Once

func ReadAllNetworkUsage() map[string]NetworkUsage {
	interfaceNamesOnce.Do(func() {
		interfaces, _ := net.Interfaces()
		for _, iface := range interfaces {
			if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
				continue
			}

			interfaceNames = append(interfaceNames, iface.Name)
		}
	})

	result := map[string]NetworkUsage{}
	for _, name := range interfaceNames {
		read, write := ReadNetworkUsage(name)
		result[name] = NetworkUsage{
			Read:  read,
			Write: write,
		}
	}

	return result
}

func ReadNetworkUsage(interfaceName string) (uint64, uint64) {
	bytesReadRaw := readFirstLine(fmt.Sprintf("/sys/class/net/%s/statistics/rx_bytes", interfaceName))
	bytesWrittenRaw := readFirstLine(fmt.Sprintf("/sys/class/net/%s/statistics/tx_bytes", interfaceName))

	bytesRead, _ := strconv.ParseUint(bytesReadRaw, 10, 64)
	bytesWritten, _ := strconv.ParseUint(bytesWrittenRaw, 10, 64)

	return bytesRead, bytesWritten
}

type IoStats struct {
	Read  uint64
	Write uint64
}

func ReadIoStats() (IoStats, error) {
	if isCgroupV2() {
		return readCgroupV2IO()
	} else {
		return readCgroupV1IO()
	}
}

type NvidiaStats struct {
	Utilization      float64 // 1-100
	MemoryTotalBytes uint64
	MemoryUsedBytes  uint64
}

func ReadNvidiaGpuUsage() []NvidiaStats {
	// With two GPUs present:
	// nvidia-smi --query-gpu=utilization.gpu,memory.total,memory.used --format=csv,noheader,nounits
	// 0, 81559, 1
	// 0, 81559, 1

	// usage is in percentage (1-100)
	// memory is in MiB

	stdout, _, ok := util.RunCommand([]string{
		"nvidia-smi",
		"--query-gpu=utilization.gpu,memory.total,memory.used",
		"--format=csv,noheader,nounits",
	})

	if !ok {
		return nil
	}

	var result []NvidiaStats

	lines := strings.Split(stdout, "\n")
	for _, line := range lines {
		cols := strings.Split(line, ",")
		if len(cols) == 3 {
			usage, err1 := strconv.ParseFloat(strings.TrimSpace(cols[0]), 64)
			memTotal, err2 := strconv.ParseUint(strings.TrimSpace(cols[1]), 10, 64)
			memUsed, err3 := strconv.ParseUint(strings.TrimSpace(cols[2]), 10, 64)

			if err1 == nil && err2 == nil && err3 == nil {
				result = append(result, NvidiaStats{
					Utilization:      usage,
					MemoryTotalBytes: memTotal * 1024 * 1024,
					MemoryUsedBytes:  memUsed * 1024 * 1024,
				})
			}
		}
	}

	return result
}

type CpuStats struct {
	Usage float64 // 100 = 100% of single vCPU.
	Limit float64
}

// Helper function to read the first line of a file
func readFirstLine(filename string) string {
	file, err := os.Open(filename)
	if err != nil {
		return ""
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	if scanner.Scan() {
		return scanner.Text()
	}
	return ""
}

// Detects whether cgroup v2 is being used
func isCgroupV2() bool {
	_, err := os.Stat(cgroupV2CPUStat)
	return err == nil
}

func isMemoryRestrictedByCgroup() bool {
	_, ok := readCgroupMemoryLimitIfRestricted()
	return ok
}

func readCgroupMemoryLimitIfRestricted() (uint64, bool) {
	if isCgroupV2() {
		line := readFirstLine("/sys/fs/cgroup/memory.max")
		if line == "" || line == "max" {
			return 0, false
		}

		limit, err := strconv.ParseUint(line, 10, 64)
		if err != nil || limit == 0 {
			return 0, false
		}

		return limit, true
	}

	line := readFirstLine("/sys/fs/cgroup/memory/memory.limit_in_bytes")
	if line == "" {
		return 0, false
	}

	limit, err := strconv.ParseUint(line, 10, 64)
	if err != nil || limit == 0 {
		return 0, false
	}

	if limit > (1 << 60) {
		return 0, false
	}

	return limit, true
}

func readSystemMemoryUsage() uint64 {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return 0
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	memoryTotalKb := uint64(0)
	memoryFreeKb := uint64(0)
	buffersKb := uint64(0)
	cachedKb := uint64(0)
	sreclaimableKb := uint64(0)
	shmemKb := uint64(0)
	for scanner.Scan() {
		line := scanner.Text()
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}

		switch fields[0] {
		case "MemTotal:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				memoryTotalKb = value
			}
		case "MemFree:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				memoryFreeKb = value
			}
		case "Buffers:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				buffersKb = value
			}
		case "Cached:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				cachedKb = value
			}
		case "SReclaimable:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				sreclaimableKb = value
			}
		case "Shmem:":
			value, err := strconv.ParseUint(fields[1], 10, 64)
			if err == nil {
				shmemKb = value
			}
		}
	}

	if memoryTotalKb == 0 {
		return 0
	}

	cacheKb := buffersKb + cachedKb + sreclaimableKb
	if cacheKb >= shmemKb {
		cacheKb -= shmemKb
	} else {
		cacheKb = 0
	}

	usedKb := memoryTotalKb
	if memoryFreeKb+cacheKb < memoryTotalKb {
		usedKb = memoryTotalKb - memoryFreeKb - cacheKb
	} else {
		usedKb = 0
	}

	return usedKb * 1024
}

func readSystemMemoryLimit() uint64 {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return 0
	}
	defer util.SilentClose(file)

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if !strings.HasPrefix(line, "MemTotal:") {
			continue
		}

		fields := strings.Fields(line)
		if len(fields) < 2 {
			return 0
		}

		memoryKb, err := strconv.ParseUint(fields[1], 10, 64)
		if err != nil {
			return 0
		}

		return memoryKb * 1024
	}

	return 0
}
