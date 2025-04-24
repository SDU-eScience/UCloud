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
	line := readFirstLine("/sys/fs/cgroup/memory.current") // cgroup v2
	if line == "" {
		line = readFirstLine("/sys/fs/cgroup/memory/memory.usage_in_bytes") // cgroup v1
	}

	memoryUsed, _ := strconv.ParseUint(line, 10, 64)
	return memoryUsed
}

func ReadMemoryLimit() uint64 {
	line := readFirstLine("/sys/fs/cgroup/memory.max") // cgroup v2
	if line == "" {
		line = readFirstLine("/sys/fs/cgroup/memory/memory.limit_in_bytes") // cgroup v1
	}
	memoryLimit, _ := strconv.ParseUint(line, 10, 64)
	return memoryLimit
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
					MemoryTotalBytes: memTotal,
					MemoryUsedBytes:  memUsed,
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
