package ucmetrics

import (
	"bufio"
	"os"
	"strconv"
	"strings"
	"ucloud.dk/pkg/util"
)

const (
	cgroupV2IOStat = "/sys/fs/cgroup/io.stat"
	cgroupV1IOStat = "/sys/fs/cgroup/blkio/blkio.throttle.io_service_bytes"
)

// Reads and aggregates total read/write bytes from cgroup v2
func readCgroupV2IO() (IoStats, error) {
	file, err := os.Open(cgroupV2IOStat)
	if err != nil {
		return IoStats{}, err
	}
	defer util.SilentClose(file)

	var totalRead, totalWrite uint64
	scanner := bufio.NewScanner(file)

	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())

		// Example: "8:0 rbytes=12345678 wbytes=87654321 rios=123 wios=456"
		for _, field := range fields[1:] {
			kv := strings.Split(field, "=")
			if len(kv) != 2 {
				continue
			}
			value, err := strconv.ParseUint(kv[1], 10, 64)
			if err != nil {
				continue
			}

			switch kv[0] {
			case "rbytes":
				totalRead += value
			case "wbytes":
				totalWrite += value
			}
		}
	}

	if err := scanner.Err(); err != nil {
		return IoStats{}, err
	}
	return IoStats{Read: totalRead, Write: totalWrite}, nil
}

// Reads and aggregates total read/write bytes from cgroup v1
func readCgroupV1IO() (IoStats, error) {
	file, err := os.Open(cgroupV1IOStat)
	if err != nil {
		return IoStats{}, err
	}
	defer util.SilentClose(file)

	var totalRead, totalWrite uint64
	scanner := bufio.NewScanner(file)

	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) != 3 {
			continue
		}

		value, err := strconv.ParseUint(fields[2], 10, 64)
		if err != nil {
			continue
		}

		switch fields[1] {
		case "Read":
			totalRead += value
		case "Write":
			totalWrite += value
		}
	}

	if err := scanner.Err(); err != nil {
		return IoStats{}, err
	}
	return IoStats{Read: totalRead, Write: totalWrite}, nil
}
