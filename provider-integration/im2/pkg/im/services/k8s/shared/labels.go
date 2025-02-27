package shared

import "ucloud.dk/pkg/util"

func JobIdLabel(jobId string) util.Tuple2[string, string] {
	return util.Tuple2[string, string]{"ucloud.dk/jobId", jobId}
}
