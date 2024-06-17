package gpfs

import (
	"encoding/json"
	"fmt"
	"gopkg.in/yaml.v3"
	"io"
	"net/http"
	"os"
	"os/user"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type mockConfigFile struct {
	Port                        int                          `yaml:"port"`
	Username                    string                       `yaml:"username"`
	Password                    string                       `yaml:"password"`
	PreconfiguredSystemsAndSets map[string]map[string]string `yaml:"preconfiguredSystemsAndSets"`
}

var mockConfig mockConfigFile

var mockDatabaseLock sync.Mutex

type mockDatabase struct {
	Filesets []FilesetResponse
	Quotas   []QuotaResponse
}

func openMockDatabase() *mockDatabase {
	mockDatabaseLock.Lock()

	data, err := os.ReadFile("/etc/ucloud/gpfs_mock.db")
	var db *mockDatabase

	if err != nil {
		log.Info("Initializing database!")
		db = &mockDatabase{}

		log.Info("Configured systems: %v", mockConfig.PreconfiguredSystemsAndSets)
		for system, sets := range mockConfig.PreconfiguredSystemsAndSets {
			for setKey, _ := range sets {
				filesetResponse := FilesetResponse{}
				filesetResponse.Filesets = append(filesetResponse.Filesets, FilesetResponseItem{})
				set := &filesetResponse.Filesets[0]
				set.FilesetName = setKey
				set.FilesystemName = system

				quotaResponse := QuotaResponse{}
				quotaResponse.Quotas = append(quotaResponse.Quotas, QuotaResponseItem{})
				quota := &quotaResponse.Quotas[0]
				quota.FilesystemName = system
				quota.ObjectName = setKey

				db.Filesets = append(db.Filesets, filesetResponse)
				db.Quotas = append(db.Quotas, quotaResponse)
			}
		}
	} else {
		err = json.Unmarshal(data, &db)
		if err != nil {
			log.Info("Corrupt gpfs mock database: %v", err)
			db = &mockDatabase{}
		}
	}

	return db
}

func (db *mockDatabase) Close() {
	defer mockDatabaseLock.Unlock()

	data, err := json.Marshal(db)
	if err != nil {
		log.Warn("Failed to marshall DB: %v", err)
		return
	}

	err = os.WriteFile("/etc/ucloud/gpfs_mock.db", data, 0600)
	if err != nil {
		log.Warn("Failed to write mock database: %v", err)
		return
	}
}

func RunMockServer() {
	log.Info("Starting up...")
	if os.Getuid() != 0 {
		log.Error("This mock server requires root to be able to create the required filesets (i.e. folders)")
		return
	}
	err := log.SetLogFile("/var/log/ucloud/gpfs_mock.log")
	if err != nil {
		log.Error("Failed to update log file: %v", err)
		return
	}
	data, err := os.ReadFile("/etc/ucloud/gpfs_mock.yml")
	if err != nil {
		log.Error("Could not read log file at: /etc/ucloud/gpfs_mock.yml %v", err)
		return
	}

	err = yaml.Unmarshal(data, &mockConfig)
	if err != nil {
		log.Error("Could not read config file: %v", err)
		return
	}

	baseUrl := "/scalemgmt/v2/"

	log.Info("Starting GPFS mock server on port %v", mockConfig.Port)
	err = http.ListenAndServe(
		fmt.Sprintf(":%v", mockConfig.Port),
		http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
			method := request.Method
			if !strings.HasPrefix(request.URL.Path, baseUrl) {
				writer.WriteHeader(http.StatusNotFound)
				return
			}

			username, password, _ := request.BasicAuth()
			if mockConfig.Username != username {
				writer.WriteHeader(http.StatusUnauthorized)
				return
			}
			if mockConfig.Password != password {
				writer.WriteHeader(http.StatusUnauthorized)
				return
			}

			query := request.URL.Query()
			path := strings.Split(strings.TrimPrefix(request.URL.Path, baseUrl), "/")
			l := len(path)

			log.Info("%v %v %v", method, path, l)

			if method == http.MethodGet && l == 1 && c(path, 0) == "info" {
				// TODO(Dan): I have no idea what this endpoint normally sends, but our client doesn't care.
				_, _ = writer.Write([]byte("{\"status\": \"OK\"}"))
				return
			}

			if method == http.MethodGet && l == 4 && c(path, 0) == "filesystems" && c(path, 2) == "filesets" {
				resp, ok := mockGetFileset(c(path, 1), c(path, 3))
				sendResp(writer, resp, ok)
			} else if method == http.MethodGet && l == 3 && c(path, 0) == "filesystems" && c(path, 2) == "quotas" {
				filters := strings.Split(query.Get("filter"), ",")
				allFilters := make(map[string]string, len(filters))
				for _, filter := range filters {
					filterSplit := strings.Split(filter, "=")
					if len(filterSplit) != 2 {
						continue
					}

					allFilters[filterSplit[0]] = filterSplit[1]
				}

				filesystem := c(path, 1)
				fileset := allFilters["objectName"]

				resp, ok := mockFilesetGetQuota(filesystem, fileset)
				sendResp(writer, resp, ok)
			} else if method == http.MethodGet && l == 2 && c(path, 0) == "jobs" {
				jobId, _ := strconv.Atoi(c(path, 1))
				resp, ok := mockGetJobResponse(jobId)
				sendResp(writer, resp, ok)
			} else if method == http.MethodPost && l == 3 && c(path, 0) == "filesystems" && c(path, 2) == "filesets" {
				success := true
				type req struct {
					FilesetName string
					InodeSpace  string
					Comment     string
					Path        string
					Owner       string
					Permissions string
				}

				filesystem := c(path, 1)
				params := readParams[req](request, &success)
				if !success {
					writer.WriteHeader(http.StatusBadRequest)
					return
				}

				resp, ok := mockCreateFileset(&Fileset{
					Name:        params.FilesetName,
					Filesystem:  filesystem,
					Description: params.Comment,
					Path:        params.Path,
					Permissions: params.Permissions,
					Owner:       params.Owner,
					Parent:      params.InodeSpace,
					Created:     time.Now(),
					QuotaBytes:  0,
					UsageBytes:  0,
					QuotaFiles:  0,
					UsageFiles:  0,
				})

				sendResp(writer, resp, ok)
			} else if method == http.MethodPost && l == 3 && c(path, 0) == "filesystems" && c(path, 2) == "quotas" {
				success := true
				type req struct {
					ObjectName     string
					BlockSoftLimit int
					BlockHardLimit int
					FilesSoftLimit int
					FilesHardLimit int
				}
				params := readParams[req](request, &success)
				filesystem := c(path, 1)

				if !success {
					writer.WriteHeader(http.StatusBadRequest)
					return
				}

				resp, ok := mockFilesetSetQuota(filesystem, params.ObjectName, params.BlockHardLimit, params.FilesHardLimit)
				sendResp(writer, resp, ok)
			} else if method == http.MethodDelete && l == 5 && c(path, 0) == "filesystems" && c(path, 2) == "filesets" && c(path, 4) == "link" {
				filesystem := c(path, 1)
				fileset := c(path, 3)

				resp, ok := mockFilesetUnlink(filesystem, fileset)
				sendResp(writer, resp, ok)
			} else if method == http.MethodDelete && l == 4 && c(path, 0) == "filesystems" && c(path, 2) == "filesets" {
				filesystem := c(path, 1)
				fileset := c(path, 3)

				resp, ok := mockFilesetDelete(filesystem, fileset)
				sendResp(writer, resp, ok)
			}
		}),
	)

	log.Error("Mock server has terminated early: %v", err)
}

func sendResp(writer http.ResponseWriter, data any, ok bool) {
	if !ok {
		writer.WriteHeader(http.StatusBadRequest)
		return
	}

	jsonBytes, _ := json.Marshal(data)
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(http.StatusOK)
	_, _ = writer.Write(jsonBytes)
}

func c(components []string, idx int) string {
	if idx < 0 || idx >= len(components) {
		return ""
	}
	return components[idx]
}

func readParams[T any](request *http.Request, success *bool) T {
	var result T
	reader := request.Body
	if reader == nil {
		log.Warn("%v Missing body?", util.GetCaller())
		*success = false
		return result
	}

	defer util.SilentClose(reader)
	data, err := io.ReadAll(reader)
	if err != nil {
		log.Warn("%v Unable to read body? %v", util.GetCaller(), err)
		*success = false
		return result
	}

	err = json.Unmarshal(data, &result)
	if err != nil {
		log.Warn("%v Unable to parse body? %v", util.GetCaller(), err)
		*success = false
		return result
	}

	return result
}

func mockGetFileset(filesystem, fileset string) (FilesetResponse, bool) {
	mockData := openMockDatabase()
	defer mockData.Close()
	for _, fs := range mockData.Filesets {
		for _, set := range fs.Filesets {
			if set.FilesetName == fileset && set.FilesystemName == filesystem {
				return fs, true
			}
		}
	}
	return FilesetResponse{}, false
}

func mockCreateFileset(f *Fileset) (JobResponse, bool) {
	mockData := openMockDatabase()
	defer mockData.Close()

	rootPath, ok := mockConfig.PreconfiguredSystemsAndSets[f.Filesystem][f.Parent]
	if !ok {
		log.Error("Could not find requested filesystem and parent set: %v/%v", f.Filesystem, f.Parent)
		return dummyJobResponse(), false
	}

	ownerSplit := strings.Split(f.Owner, ":")
	if len(ownerSplit) == 1 {
		ownerSplit = append(ownerSplit, ownerSplit[0])
	}
	if len(ownerSplit) != 2 {
		log.Error("Invalid owner passed! %v", f.Owner)
		return dummyJobResponse(), false
	}

	mode, err := strconv.ParseInt(f.Permissions, 8, 32)
	if err != nil {
		log.Error("Could not parse mode: %v %v", f.Permissions, err)
		return dummyJobResponse(), false
	}

	uinfo, err := user.Lookup(ownerSplit[0])
	if err != nil {
		log.Error("Could not look up user: %v %v", ownerSplit[0], err)
		return dummyJobResponse(), false
	}
	uid, _ := strconv.Atoi(uinfo.Uid)

	ginfo, err := user.LookupGroup(ownerSplit[1])
	if err != nil {
		log.Error("Could not look up group: %v %v", ownerSplit[1], err)
		return dummyJobResponse(), false
	}
	gid, _ := strconv.Atoi(ginfo.Gid)

	pathComponents := util.Components(f.Path)
	if len(pathComponents) != 3 {
		log.Error("The mock server assumes that all paths must have length 3, but this one did not! %v", f.Path)
		return dummyJobResponse(), false
	}

	actualDirectory := filepath.Join(rootPath, pathComponents[2])
	err = os.Mkdir(actualDirectory, os.FileMode(mode))
	if err != nil {
		stat, err2 := os.Stat(actualDirectory)
		if err2 != nil || !stat.IsDir() {
			log.Error("Failed at creating fileset directory at %v: %v", actualDirectory, err)
			return dummyJobResponse(), false
		}
	}

	err = os.Chown(actualDirectory, uid, gid)
	if err != nil {
		log.Error("Failed at setting owner of fileset directory at %v: %v", actualDirectory, err)
		return dummyJobResponse(), false
	}

	err = os.Chmod(actualDirectory, os.FileMode(mode))
	if err != nil {
		log.Error("Failed at setting mode of fileset directory at %v: %v", actualDirectory, err)
		return dummyJobResponse(), false
	}
	var fs FilesetResponse
	fs.Filesets = append(fs.Filesets, FilesetResponseItem{})
	set := &fs.Filesets[0]
	set.FilesetName = f.Name
	set.FilesystemName = f.Filesystem
	set.Config.Comment = f.Description
	set.Config.Path = f.Path

	var quotaResp QuotaResponse
	quotaResp.Quotas = append(quotaResp.Quotas, QuotaResponseItem{})
	quota := &quotaResp.Quotas[0]
	quota.QuotaID = 4242
	quota.BlockUsage = 0
	quota.BlockQuota = f.QuotaBytes
	quota.FilesUsage = 0
	quota.FilesLimit = f.QuotaFiles
	quota.ObjectName = f.Name
	quota.FilesystemName = f.Filesystem

	mockData.Filesets = append(mockData.Filesets, fs)
	mockData.Quotas = append(mockData.Quotas, quotaResp)

	return dummyJobResponse(), true
}

func mockFilesetGetQuota(filesystem, fileset string) (QuotaResponse, bool) {
	mockData := openMockDatabase()
	defer mockData.Close()

	for _, quota := range mockData.Quotas {
		q := &quota.Quotas[0]
		if q.FilesystemName == filesystem && q.ObjectName == fileset {
			return quota, true
		}
	}
	return QuotaResponse{}, false
}

func mockFilesetSetQuota(filesystem, fileset string, quotaBytes, quotaFiles int) (JobResponse, bool) {
	mockData := openMockDatabase()
	defer mockData.Close()

	for i, _ := range mockData.Quotas {
		quota := &mockData.Quotas[i]
		q := &quota.Quotas[0]
		if q.FilesystemName == filesystem && q.ObjectName == fileset {
			q.BlockQuota = quotaBytes
			q.FilesQuota = quotaFiles
			return dummyJobResponse(), true
		}
	}
	return dummyJobResponse(), false
}

func mockFilesetUnlink(filesystem, fileset string) (JobResponse, bool) {
	return dummyJobResponse(), false
}

func mockFilesetDelete(filesystem, fileset string) (JobResponse, bool) {
	return dummyJobResponse(), false
}

func mockGetJobResponse(id int) (JobResponse, bool) {
	result := JobResponse{}
	result.Jobs = append(result.Jobs, JobResponseItem{})
	item := &result.Jobs[0]
	item.JobID = id
	item.Status = "COMPLETED"
	return result, true
}

func dummyJobResponse() JobResponse {
	return JobResponse{
		Jobs: []JobResponseItem{
			{JobID: 4242, Status: "COMPLETED"},
		},
	}
}
