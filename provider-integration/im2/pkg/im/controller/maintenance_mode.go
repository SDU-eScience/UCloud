package controller

import (
	"encoding/json"
	"net/http"
	"slices"
	"ucloud.dk/shared/pkg/util"
)

var MaintenanceMode = false
var MaintenanceAllowlist []string

func MaintenanceCheck(w http.ResponseWriter, r *http.Request) bool {
	if MaintenanceMode {
		// TODO Does not work in Slurm mode

		username := GetUCloudUsername(r)
		if username == "_guest" || username == "" {
			// Assume that this request is normally supposed to go through and/or is caught by normal auth. This block
			// will also include service-to-service requests.
			return true
		}

		if slices.Contains(MaintenanceAllowlist, username) {
			return true
		}

		// NOTE(Dan): The Core currently refuse to show any of the 5XX results, so we use a 4XX return code instead.
		err := util.HttpErr(http.StatusNotFound, "Service is currently undergoing maintenance.")
		w.WriteHeader(err.StatusCode)
		errBytes, _ := json.Marshal(err)
		_, _ = w.Write(errBytes)
		return false
	} else {
		return true
	}
}
