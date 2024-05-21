package gpfs

import (
	"encoding/json"
	"time"
)

type StatusReponse struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type FilesetReponse struct {
	Filesets []struct {
		Config struct {
			Comment              string `json:"comment"`
			Created              string `json:"created"`
			IamMode              string `json:"iamMode"`
			ID                   int    `json:"id"`
			InodeSpace           int    `json:"inodeSpace"`
			InodeSpaceMask       int    `json:"inodeSpaceMask"`
			IsInodeSpaceOwner    bool   `json:"isInodeSpaceOwner"`
			MaxNumInodes         int    `json:"maxNumInodes"`
			OID                  int    `json:"oid"`
			ParentID             int    `json:"parentId"`
			Path                 string `json:"path"`
			PermissionChangeMode string `json:"permissionChangeMode"`
			RootInode            int    `json:"rootInode"`
			SnapID               int    `json:"snapId"`
			Status               string `json:"status"`
		} `json:"config"`
		FilesetName    string `json:"filesetName"`
		FilesystemName string `json:"filesystemName"`
		Usage          struct {
			AllocatedInodes      int `json:"allocatedInodes"`
			InodeSpaceFreeInodes int `json:"inodeSpaceFreeInodes"`
			InodeSpaceUsedInodes int `json:"inodeSpaceUsedInodes"`
			UsedBytes            int `json:"usedBytes"`
			UsedInodes           int `json:"usedInodes"`
		} `json:"usage"`
	} `json:"filesets"`
	Status StatusReponse `json:"status"`
}

type QuotaResponse struct {
	Quotas []struct {
		BlockGrace     string `json:"blockGrace"`
		BlockInDoubt   int    `json:"blockInDoubt"`
		BlockLimit     int    `json:"blockLimit"`
		BlockQuota     int    `json:"blockQuota"`
		BlockUsage     int    `json:"blockUsage"`
		FilesGrace     string `json:"filesGrace"`
		FilesInDoubt   int    `json:"filesInDoubt"`
		FilesLimit     int    `json:"filesLimit"`
		FilesQuota     int    `json:"filesQuota"`
		FilesUsage     int    `json:"filesUsage"`
		FilesystemName string `json:"filesystemName"`
		IsDefaultQuota bool   `json:"isDefaultQuota"`
		ObjectID       int    `json:"objectId"`
		ObjectName     string `json:"objectName"`
		QuotaID        int    `json:"quotaId"`
		QuotaType      string `json:"quotaType"`
	} `json:"quotas"`
	Status StatusReponse `json:"status"`
}

type JobResponse struct {
	Jobs []struct {
		JobID     int    `json:"jobId"`
		Status    string `json:"status"`
		Submitted string `json:"submitted"`
		Completed string `json:"completed"`
		Runtime   int    `json:"runtime"`
		Request   struct {
			Data json.RawMessage `json:"data"`
			Type string          `json:"type"`
			URL  string          `json:"url"`
		} `json:"request"`
		Result struct {
			Progress []string `json:"progress"`
			Commands []string `json:"commands"`
			Stdout   []string `json:"stdout"`
			Stderr   []string `json:"stderr"`
			ExitCode int      `json:"exitCode"`
		} `json:"result"`
		Pids json.RawMessage `json:"pids"`
	} `json:"jobs"`
	Status StatusReponse `json:"status"`
}

type Fileset struct {
	Name        string
	Filesystem  string
	Description string
	Path        string
	Permissions string
	Owner       string
	Parent      string
	Created     time.Time
	QuotaBytes  int
	UsageBytes  int
	QuotaFiles  int
	UsageFiles  int
}
