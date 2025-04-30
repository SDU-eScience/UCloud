package foundation

import (
	"sync"
	fndapi "ucloud.dk/shared/pkg/foundation"
)

type internalProjectBucket struct {
	Mu       sync.RWMutex
	Members  []internalProjectMembership
	Projects []internalProject
}

type internalProjectMembership struct {
	Username string
	Mu       sync.Mutex
	Projects []string
	Groups   []string
}

type internalProject struct {
	Id      string
	Mu      sync.Mutex
	Project fndapi.Project
}

func projectRetrieveInternal() {

}
