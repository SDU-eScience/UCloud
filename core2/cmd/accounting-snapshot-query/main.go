package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"slices"
	"strings"

	"gopkg.in/yaml.v3"
)

type finding struct {
	Code            string  `yaml:"code"`
	WalletIds       []int64 `yaml:"walletIds,omitempty"`
	GroupIds        []int64 `yaml:"groupIds,omitempty"`
	AllocationIds   []int64 `yaml:"allocationIds,omitempty"`
	PersistedValue  *int64  `yaml:"persistedValue,omitempty"`
	RecomputedValue *int64  `yaml:"recomputedValue,omitempty"`
	Details         string  `yaml:"details"`
	Impact          string  `yaml:"impact,omitempty"`
}

type bucket struct {
	Provider string    `yaml:"provider"`
	Category string    `yaml:"category"`
	Findings []finding `yaml:"findings"`
}

type report struct {
	Findings []finding `yaml:"findings"`
	Buckets  []bucket  `yaml:"buckets"`
}

type matchedFinding struct {
	Bucket          string  `yaml:"bucket,omitempty"`
	Code            string  `yaml:"code"`
	WalletIds       []int64 `yaml:"walletIds,omitempty"`
	GroupIds        []int64 `yaml:"groupIds,omitempty"`
	AllocationIds   []int64 `yaml:"allocationIds,omitempty"`
	PersistedValue  *int64  `yaml:"persistedValue,omitempty"`
	RecomputedValue *int64  `yaml:"recomputedValue,omitempty"`
	Details         string  `yaml:"details"`
	Impact          string  `yaml:"impact,omitempty"`
}

func main() {
	file := flag.String("file", "-", "snapshot YAML file, or - for stdin")
	codesArg := flag.String("code", "", "comma-separated error codes")
	bucketArg := flag.String("bucket", "", "category or provider/category")
	limit := flag.Int("limit", 100, "maximum failures to print")
	counts := flag.Bool("counts", false, "print aggregate counts by code")
	countsByBucket := flag.Bool("counts-by-bucket", false, "print counts by code within each bucket")
	flag.Parse()

	if *limit < 0 {
		fail("limit must be at least zero")
	}
	if *counts && *countsByBucket {
		fail("counts and counts-by-bucket cannot be used together")
	}

	input := io.Reader(os.Stdin)
	if *file != "-" {
		f, err := os.Open(*file)
		if err != nil {
			fail(err.Error())
		}
		defer f.Close()
		input = f
	}

	var snapshot report
	if err := yaml.NewDecoder(input).Decode(&snapshot); err != nil {
		fail(fmt.Sprintf("could not decode snapshot: %v", err))
	}

	codes := map[string]bool{}
	for _, code := range strings.Split(*codesArg, ",") {
		if code = strings.TrimSpace(code); code != "" {
			codes[code] = true
		}
	}

	var matches []matchedFinding
	if *bucketArg == "" {
		for _, item := range snapshot.Findings {
			if len(codes) == 0 || codes[item.Code] {
				matches = append(matches, convertFinding("", item))
			}
		}
	}
	for _, b := range snapshot.Buckets {
		bucketName := b.Provider + "/" + b.Category
		if *bucketArg != "" && *bucketArg != b.Category && *bucketArg != bucketName {
			continue
		}
		for _, item := range b.Findings {
			if len(codes) == 0 || codes[item.Code] {
				matches = append(matches, convertFinding(bucketName, item))
			}
		}
	}

	if *counts {
		printCounts(matches)
		return
	}
	if *countsByBucket {
		printBucketCounts(matches)
		return
	}

	shown := min(*limit, len(matches))
	output := struct {
		Matched  int              `yaml:"matched"`
		Shown    int              `yaml:"shown"`
		Failures []matchedFinding `yaml:"failures"`
	}{
		Matched:  len(matches),
		Shown:    shown,
		Failures: matches[:shown],
	}
	encoded, err := yaml.Marshal(output)
	if err != nil {
		fail(err.Error())
	}
	_, _ = os.Stdout.Write(encoded)
}

func convertFinding(bucketName string, item finding) matchedFinding {
	return matchedFinding{
		Bucket:          bucketName,
		Code:            item.Code,
		WalletIds:       item.WalletIds,
		GroupIds:        item.GroupIds,
		AllocationIds:   item.AllocationIds,
		PersistedValue:  item.PersistedValue,
		RecomputedValue: item.RecomputedValue,
		Details:         item.Details,
		Impact:          item.Impact,
	}
}

func printCounts(matches []matchedFinding) {
	counts := map[string]int{}
	for _, item := range matches {
		counts[item.Code]++
	}
	codes := make([]string, 0, len(counts))
	for code := range counts {
		codes = append(codes, code)
	}
	slices.SortFunc(codes, func(a, b string) int {
		if counts[a] != counts[b] {
			return counts[b] - counts[a]
		}
		return strings.Compare(a, b)
	})
	for _, code := range codes {
		fmt.Printf("%6d  %s\n", counts[code], code)
	}
	fmt.Printf("%6d  total\n", len(matches))
}

func printBucketCounts(matches []matchedFinding) {
	counts := map[string]map[string]int{}
	totals := map[string]int{}
	for _, item := range matches {
		if item.Bucket != "" {
			if counts[item.Bucket] == nil {
				counts[item.Bucket] = map[string]int{}
			}
			counts[item.Bucket][item.Code]++
			totals[item.Bucket]++
		}
	}
	buckets := make([]string, 0, len(counts))
	for bucket := range counts {
		buckets = append(buckets, bucket)
	}
	slices.SortFunc(buckets, func(a, b string) int {
		if totals[a] != totals[b] {
			return totals[b] - totals[a]
		}
		return strings.Compare(a, b)
	})
	total := 0
	for _, bucket := range buckets {
		fmt.Printf("%s  (%d)\n", bucket, totals[bucket])
		codes := make([]string, 0, len(counts[bucket]))
		for code := range counts[bucket] {
			codes = append(codes, code)
		}
		slices.SortFunc(codes, func(a, b string) int {
			if counts[bucket][a] != counts[bucket][b] {
				return counts[bucket][b] - counts[bucket][a]
			}
			return strings.Compare(a, b)
		})
		for _, code := range codes {
			fmt.Printf("%6d  %s\n", counts[bucket][code], code)
		}
		total += totals[bucket]
	}
	fmt.Printf("total  (%d)\n", total)
}

func fail(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
