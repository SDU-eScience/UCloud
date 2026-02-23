package ucmetrics

import (
	"bufio"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
	"syscall"
	"time"
)

var monitorOsNext = time.Now()

func monitorOsStats() {
	if time.Now().Before(monitorOsNext) {
		return
	}

	stats := map[string]string{}

	stats["OperatingSystem"] = ""
	stats["DiskTotal"] = "0"
	stats["DiskUsed"] = "0"
	stats["Uptime"] = "0"
	stats["KernelVersion"] = ""
	stats["PrivateIPAddress"] = ""
	stats["BootID"] = ""
	stats["LoadAverage15m"] = "0"

	if file, err := os.Open("/etc/os-release"); err == nil {
		scanner := bufio.NewScanner(file)
		for scanner.Scan() {
			line := scanner.Text()
			if strings.HasPrefix(line, "PRETTY_NAME=") {
				value := strings.TrimPrefix(line, "PRETTY_NAME=")
				stats["OperatingSystem"] = strings.Trim(value, `"`)
				break
			}
		}
		_ = file.Close()
	}

	var fs syscall.Statfs_t
	if err := syscall.Statfs("/", &fs); err != nil {
		fs = syscall.Statfs_t{}
	}

	if fs.Blocks > 0 && fs.Bsize > 0 {
		total := fs.Blocks * uint64(fs.Bsize)
		free := fs.Bfree * uint64(fs.Bsize)
		used := uint64(0)
		if free < total {
			used = total - free
		}

		stats["DiskTotal"] = strconv.FormatUint(total, 10)
		stats["DiskUsed"] = strconv.FormatUint(used, 10)
	}

	if rawUptime, err := os.ReadFile("/proc/uptime"); err == nil {
		fields := strings.Fields(string(rawUptime))
		if len(fields) > 0 {
			if uptimeFloat, parseErr := strconv.ParseFloat(fields[0], 64); parseErr == nil {
				stats["Uptime"] = strconv.FormatInt(int64(uptimeFloat), 10)
			}
		}
	}

	if rawKernel, err := os.ReadFile("/proc/sys/kernel/osrelease"); err == nil {
		stats["KernelVersion"] = strings.TrimSpace(string(rawKernel))
	}

	if rawBootID, err := os.ReadFile("/proc/sys/kernel/random/boot_id"); err == nil {
		stats["BootID"] = strings.TrimSpace(string(rawBootID))
	}

	if rawLoadAvg, err := os.ReadFile("/proc/loadavg"); err == nil {
		fields := strings.Fields(string(rawLoadAvg))
		if len(fields) >= 3 {
			stats["LoadAverage15m"] = fields[2]
		}
	}

	if interfaces, err := net.Interfaces(); err == nil {
		fallbackIP := ""
		for _, iface := range interfaces {
			if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
				continue
			}

			addrs, addrErr := iface.Addrs()
			if addrErr != nil {
				continue
			}

			for _, addr := range addrs {
				ipNet, ok := addr.(*net.IPNet)
				if !ok {
					continue
				}

				ip := ipNet.IP
				if ip == nil || ip.IsLoopback() {
					continue
				}

				ip4 := ip.To4()
				if ip4 == nil {
					continue
				}

				ipString := ip4.String()
				if ip4.IsPrivate() {
					stats["PrivateIPAddress"] = ipString
					break
				}

				if fallbackIP == "" {
					fallbackIP = ipString
				}
			}

			if stats["PrivateIPAddress"] != "" {
				break
			}
		}

		if stats["PrivateIPAddress"] == "" {
			stats["PrivateIPAddress"] = fallbackIP
		}
	}

	statString := &strings.Builder{}
	for k, v := range stats {
		statString.WriteString(fmt.Sprintf("%s:%s\n", k, v))
	}
	err := os.WriteFile("/work/.ucmetrics-stats.new", []byte(statString.String()), 0660)
	if err == nil {
		_ = os.Rename("/work/.ucmetrics-stats.new", "/work/.ucmetrics-stats")
	}

	monitorOsNext = time.Now().Add(60 * time.Second)
}
