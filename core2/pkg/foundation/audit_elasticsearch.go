package foundation

import (
	"bytes"
	"encoding/json"
	"github.com/elastic/go-elasticsearch/v9"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/core/pkg/config"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
)

var elasticClient *elasticsearch.Client
var givenConfig config.ConfigurationFormat

func InitAuditElasticSearch(config config.ConfigurationFormat) func(event rpc.HttpCallLogEntry) {
	var err error
	givenConfig = config
	if config.Elasticsearch.Host.Address == "" {
		log.Info("elasticsearch host is empty")
		return nil
	}
	cfg := elasticsearch.Config{
		Addresses: []string{config.Elasticsearch.Host.ToURL()},
		Transport: http.DefaultTransport,
		Username:  config.Elasticsearch.Credentials.Username,
		Password:  config.Elasticsearch.Credentials.Password,
	}

	elasticClient, err = elasticsearch.NewClient(cfg)

	if err != nil {
		panic(err)
	}

	return pushLogsToElastic
}

const (
	YYYYMMDD          = "2006.01.02"
	YYYYMM            = "2006.01"
	DAYS_TO_KEEP_DATA = 180
	GATHER_NODE       = "ucloud-es-nodes-1"
)

/* INSERT LOGS */
func pushLogsToElastic(event rpc.HttpCallLogEntry) {
	data, err := json.Marshal(event)
	if err != nil {
		log.Info("Failed to create json for event: ", event)
		return
	}
	dateSuffix := time.Now().UTC().Format(YYYYMMDD)
	indexName := "http_logs_" + event.RequestName + "-" + dateSuffix
	elasticClient.Index(indexName, bytes.NewReader(data))
}

/* CLEANUP */
func CleanUpLogs() {
	httpLogsList := GetLogs([]string{"http_logs*"})
	now := time.Now().UTC()
	expiredDate := now.AddDate(0, 0, -DAYS_TO_KEEP_DATA).Format(YYYYMMDD)
	for _, index := range httpLogsList {
		removeExpiredLogs(index)
	}
	expiredLogs := GetLogs([]string{"*-" + expiredDate})
	for _, expiredLog := range expiredLogs {
		DeleteIndex(expiredLog)
	}
	yesterdayDateFormat := now.AddDate(0, 0, -1).Format(YYYYMMDD)
	yesterdayIndices := GetLogs([]string{"*-" + yesterdayDateFormat})
	for _, yesterdayLog := range yesterdayIndices {
		Shrink(yesterdayLog)
	}
}

// removeExpiredLogs Removes log entries that has exceeded set expiry field in given index
func removeExpiredLogs(indexName string) {
	now := time.Now().UTC().Unix()

	var buffer bytes.Buffer
	expiredQuery := map[string]interface{}{
		"query": map[string]interface{}{
			"range": map[string]interface{}{
				"expiry": map[string]interface{}{
					"lte": now,
				},
			},
		},
	}

	json.NewEncoder(&buffer).Encode(expiredQuery)

	expiredCount := countDocs(indexName, buffer.String())
	sizeOfIndex := countDocs(indexName, "")

	if expiredCount == 0 {
		log.Info("Nothing expired in index - moving on")
		return
	}
	if sizeOfIndex == expiredCount {
		log.Info("All docs are expired - faster to delete index")
		DeleteIndex(indexName)
	} else {
		_, err := elasticClient.DeleteByQuery(
			[]string{indexName},
			strings.NewReader(buffer.String()),
		)

		if err != nil {
			log.Info("Failed to delete expired logs: ", err)
			return
		}

		FlushIndex(indexName)
	}
}

// Shrink Reducing number of shards used by index to 1
func Shrink(indexName string) {
	targetIndex := indexName + "_small"
	log.Info("Shrinking index: ", indexName)
	if GetShardCount(indexName) == 1 {
		log.Info("Index is already at 1 shard")
		return
	}
	prepareSourceIndex(indexName)
	waitForRelocation(indexName)
	shrinkIndex(indexName, targetIndex)
	if IsSameSize(indexName, targetIndex) {
		DeleteIndex(indexName)
	}
}

// ReindexToMonthly Merges daily index into the monthly index
func ReindexToMonthly(indexName string) {
	minusDays := -8
	oneWeekAgo := time.Now().AddDate(0, 0, minusDays).UTC().Format(YYYYMMDD)
	monthFormat := time.Now().AddDate(0, 0, minusDays).UTC().Format(YYYYMM)
	indexNameOneWeekAgo := strings.Split(indexName, "-")[0] + "-" + oneWeekAgo
	if countDocs(indexNameOneWeekAgo, "") == 0 {
		log.Info("Index is empty - just delete it instead of attempting merge")
		DeleteIndex(indexNameOneWeekAgo)
		return
	}
	toIndex := strings.Split(indexNameOneWeekAgo, "-")[0] + "-" + monthFormat
	reindex(indexNameOneWeekAgo, toIndex)
}

/* REINDEX OPERATIONS */
func reindex(fromIndex string, toIndex string) {
	if !IndexExists(toIndex) {
		CreateIndex(toIndex)
	}

	request := map[string]interface{}{
		"source": map[string]interface{}{
			"index": fromIndex,
			"size":  2500,
		},
		"dest": map[string]interface{}{
			"index": toIndex,
		},
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(request)

	resp, err := elasticClient.Reindex(
		io.Reader(&buffer),
	)

	if err != nil {
		log.Info("Error reindexing: ", err)
		log.Info(resp.String())
	}

	fromCount := countDocs(fromIndex, "")
	toCount := countDocs(toIndex, "")
	for toCount < fromCount {
		log.Info("Waiting for reindex to complete. Working on " + fromIndex + " -> " + toIndex + "(CountFrom: " + strconv.Itoa(fromCount) + ", CountTo: " + strconv.Itoa(toCount) + ")")
		time.Sleep(time.Second * 10)
		toCount = countDocs(toIndex, "")
	}
	DeleteIndex(fromIndex)

}

/* SHRINKING OPERATIONS */

func prepareSourceIndex(indexName string) {
	//What node should the shards be collected on before shrink is performed
	//"index.routing.allocation.require._name"
	// gatherNode

	//Make sure that no more is being written to the index. Block writing.
	//"index.blocks.write" true
	var buffer bytes.Buffer
	request := map[string]interface{}{
		"index": map[string]interface{}{
			"routing": map[string]interface{}{
				"allocation": map[string]interface{}{
					"require": map[string]interface{}{
						"_name": GATHER_NODE,
					},
				},
			},
			"blocks": map[string]interface{}{
				"write": true,
			},
		},
	}

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	json.NewEncoder(&buffer).Encode(request)

	scheme := givenConfig.Elasticsearch.Host.Scheme
	host := givenConfig.Elasticsearch.Host.Address + ":" + strconv.Itoa(givenConfig.Elasticsearch.Host.Port)
	path := "/" + indexName + "/_settings"

	retries := 0
	for retries < 3 {
		httpRequest := http.Request{
			Method: "PUT",
			URL: &url.URL{
				Scheme: scheme,
				Host:   host,
				Path:   path,
			},
			Body:   io.NopCloser(&buffer),
			Header: header,
		}
		_, err := elasticClient.BaseClient.Transport.Perform(
			&httpRequest,
		)
		if err != nil {
			log.Info("Failed to perform http request: ", err)
			retries++
		} else {
			return
		}
	}
}

func waitForRelocation(indexName string) {
	counter := 0
	for GetClusterHealth().RelocatingShards > 0 {
		if counter%10 == 0 {
			log.Info("Waiting for relocation of " + indexName + " to complete")
		}
		counter++
		time.Sleep(time.Second * 1)
	}
}

func shrinkIndex(indexName string, targetIndexName string) {
	request := map[string]interface{}{
		"settings": map[string]interface{}{
			"index": map[string]interface{}{
				"number_of_shards": 1,
				"blocks": map[string]interface{}{
					"write": false,
				},
				"number_of_replicas": 1,
				"codec":              "best_compression",
				"routing": map[string]interface{}{
					"allocation": map[string]interface{}{
						"require": map[string]interface{}{
							"_name": "*",
						},
					},
				},
			},
		},
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(request)

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	scheme := givenConfig.Elasticsearch.Host.Scheme
	host := givenConfig.Elasticsearch.Host.Address + ":" + strconv.Itoa(givenConfig.Elasticsearch.Host.Port)
	path := "/" + indexName + "/_shrink/" + targetIndexName

	retries := 0
	for retries < 3 {
		httpRequest := http.Request{
			Method: "POST",
			URL: &url.URL{
				Scheme: scheme,
				Host:   host,
				Path:   path,
			},
			Body:   io.NopCloser(&buffer),
			Header: header,
		}
		_, err := elasticClient.BaseClient.Transport.Perform(
			&httpRequest,
		)
		if err != nil {
			log.Info("Failed to perform http request: ", err)
			retries++
		} else {
			return
		}
	}
}

/* REINDEX OPERATIONS */

/* UTILITY FUNCTIONS */

func GetClusterHealth() HealthResponse {
	resp, err := elasticClient.Cluster.Health()
	if err != nil {
		log.Info("Failed to get cluster health: ", err)
		return HealthResponse{}
	} else {
		bytesRead, _ := io.ReadAll(resp.Body)
		value := HealthResponse{}
		_ = json.Unmarshal(bytesRead, &value)
		return value
	}
}

// GetLogs retrieves a list of indices matching the input indicesToFind. Wildcards are accepted e.g. "htto_logs*"
func GetLogs(indicesToFind []string) []string {
	resp, err := elasticClient.Indices.Get(
		indicesToFind,
		elasticClient.Indices.Get.WithAllowNoIndices(true),
	)
	if err != nil {
		log.Info("Failed to get indices: ", err)
	}

	var result map[string]interface{}

	bytesRead, err := io.ReadAll(resp.Body)
	if err == nil {
		errs := json.Unmarshal(bytesRead, &result)
		if errs != nil {
			println("ERROR")
		}
	}
	listOfIndices := make([]string, len(result))
	i := 0
	for k, _ := range result {
		listOfIndices[i] = k
		i++
	}
	return listOfIndices
}

// IsSameSize compare doc count between firstIndexName and secondIndexName
func IsSameSize(firstIndexName string, secondIndexName string) bool {
	return countDocs(firstIndexName, "") == countDocs(secondIndexName, "")
}

// GetShardCount gets number of shard for given index
func GetShardCount(indexName string) int {
	resp, err := elasticClient.Indices.GetSettings(
		elasticClient.Indices.GetSettings.WithIndex(indexName),
	)
	if err != nil {
		log.Info("Failed to get shard Count: ", err)
		return 0
	}
	var result map[string]interface{}

	bytesRead, err := io.ReadAll(resp.Body)
	json.Unmarshal(bytesRead, &result)
	shardCount, _ := strconv.Atoi(result[indexName].(map[string]interface{})["settings"].(map[string]interface{})["index"].(map[string]interface{})["number_of_shards"].(string))
	return shardCount
}

// countDocs : returns number of documents in index (indexName).
// If no query is given it is a matchAll
func countDocs(indexName string, query string) int {
	ioReader := strings.NewReader(query)
	response, err := elasticClient.Count(
		elasticClient.Count.WithIndex(indexName),
		elasticClient.Count.WithBody(ioReader),
	)

	if err != nil {
		log.Info("Failed to count expired logs: ", err)
		return 0
	}

	readBytes, err := io.ReadAll(response.Body)
	value := CountResponseType{}
	json.Unmarshal(readBytes, &value)
	return value.Count
}

// DeleteIndex : Deletes entire given index
func DeleteIndex(indexName string) {
	if strings.Contains(indexName, "*") {
		log.Fatal("Cannot delete with wildcard. Index given " + indexName)
	}
	_, err := elasticClient.Indices.Delete([]string{indexName})
	if err != nil {
		log.Info("Failed to delete index: ", err)
	}
}

func CreateIndex(indexName string) {
	normalizedIndexName := strings.ToLower(indexName)
	resp, err := elasticClient.Indices.Create(
		normalizedIndexName,
	)
	if err != nil {
		log.Info("create index failed", err)
		log.Info("resp:", resp)
	}
}

// IndexExists Check if the given index exists in the cluster
func IndexExists(indexName string) bool {
	resp, _ := elasticClient.Indices.Exists(
		[]string{indexName},
	)
	return resp.Status() == "200 OK"
}

// FlushIndex Flushes the given index to force changes
func FlushIndex(indexName string) {
	elasticClient.Indices.Flush(elasticClient.Indices.Flush.WithIndex(indexName))
}
