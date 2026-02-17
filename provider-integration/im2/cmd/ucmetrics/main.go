package main

import (
	"fmt"
	"os"
	"time"

	"ucloud.dk/pkg/ucmetrics"
)

var allowedSampleIntervals = map[time.Duration]bool{
	250 * time.Millisecond: true,
	500 * time.Millisecond: true,
	750 * time.Millisecond: true,
	1 * time.Second:        true,
	5 * time.Second:        true,
	10 * time.Second:       true,
	30 * time.Second:       true,
	60 * time.Second:       true,
	120 * time.Second:      true,
}

func parseSampleInterval(args []string) time.Duration {
	interval := ucmetrics.DefaultSampleInterval

	if raw, ok := os.LookupEnv("UCLOUD_METRICS_SAMPLE_INTERVAL"); ok && raw != "" {
		parsed, err := time.ParseDuration(raw)
		if err != nil || !allowedSampleIntervals[parsed] {
			fmt.Fprintf(os.Stderr, "Warning: invalid UCLOUD_METRICS_SAMPLE_INTERVAL=%q, using default %s\n", raw, ucmetrics.DefaultSampleInterval)
		} else {
			interval = parsed
		}
	}

	for i := 0; i < len(args); i++ {
		arg := args[i]
		if arg == "viz" {
			continue
		}

		if arg == "--sample-interval" {
			if i+1 >= len(args) {
				fmt.Fprintf(os.Stderr, "Warning: missing value for --sample-interval, using %s\n", interval)
				break
			}
			i++
			arg = "--sample-interval=" + args[i]
		}

		const prefix = "--sample-interval="
		if len(arg) > len(prefix) && arg[:len(prefix)] == prefix {
			raw := arg[len(prefix):]
			parsed, err := time.ParseDuration(raw)
			if err != nil || !allowedSampleIntervals[parsed] {
				fmt.Fprintf(os.Stderr, "Warning: invalid --sample-interval=%q, using %s\n", raw, interval)
				continue
			}

			interval = parsed
		}
	}

	return interval
}

func main() {
	interval := parseSampleInterval(os.Args[1:])
	fmt.Fprintf(os.Stderr, "ucmetrics sample interval: %s\n", interval)

	ucmetrics.HandleCli(ucmetrics.Config{
		SampleInterval: interval,
	})
}
