package shared

import (
	"fmt"
	"time"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
)

func DurationStrToSimpleDuration(durationString string) (*orcapi.SimpleDuration, error) {
	duration, err := time.ParseDuration(durationString)
	if err != nil {
		return nil, fmt.Errorf("invalid duration string %q: %s", durationString, err)
	}
	return &orcapi.SimpleDuration{
		Hours:   int(duration / time.Hour),
		Minutes: int((duration % time.Hour) / time.Minute),
		Seconds: int((duration % time.Minute) / time.Second),
	}, nil
}
