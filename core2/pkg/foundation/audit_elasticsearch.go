package foundation

import (
	"bytes"
	"encoding/json"
	"github.com/elastic/go-elasticsearch/v9/esapi"
	"github.com/elastic/go-elasticsearch/v9/typedapi/core/count"
	"github.com/elastic/go-elasticsearch/v9/typedapi/types"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/core/pkg/config"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
)
import "github.com/elastic/go-elasticsearch/v9"

var elasticClient *elasticsearch.Client

func InitAuditElasticSearch(config config.ConfigurationFormat) func(event rpc.HttpCallLogEntry) {
	var err error
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
	DAYS_TO_KEEP_DATA = 180
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
	for _, index := range httpLogsList {
		removeExpiredLogs(index)
		if strings.Contains(index, now.Format(YYYYMMDD)) {
			yesterdayPeriodFormat := now.AddDate(0, 0, -1).Format(YYYYMMDD)
			yesterdaysIndexName := strings.Split(index, "-")[0] + "-" + yesterdayPeriodFormat
			Shrink(yesterdaysIndexName)
		}
		expiredIndexDate := now.AddDate(0, 0, DAYS_TO_KEEP_DATA).Format(YYYYMMDD)
		expiredIndexName := strings.Split(index, "-")[0] + "-" + expiredIndexDate
		DeleteIndex(expiredIndexName)
	}
	systemLogsList := GetLogs([]string{"kubernetes*", "infrastructure*"})
	for _, index := range systemLogsList {
		expiredIndexName := strings.Split(index, "-")[0] + "-" + YYYYMMDD
		DeleteIndex(expiredIndexName)
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
	log.Info("Shrinking index: ", indexName)
	if GetShardCount(indexName) == 1 {
		log.Info("Index is already at 1 shard")
		return
	}
	prepareSourceIndex(indexName)
	waitForRelocation(indexName)
	shrinkIndex(indexName)
	if IsSameSize(indexName, indexName+"_small") {
		DeleteIndex(indexName)
	}
}

// ReindexToMonthly Merges daily index into the monthly index
func ReindexToMonthly(indexName string) {}

/* SHRINKING OPERATIONS */

func prepareSourceIndex(indexName string) {}

func waitForRelocation(indexName string) {}

func shrinkIndex(indexName string) {

}

/* REINDEX OPERATIONS */

/* UTILITY FUNCTIONS */

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

func GetShardCount(indexName string) int {}

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

// FlushIndex Flushes the given index to force changes
func FlushIndex(indexName string) {
	elasticClient.Indices.Flush(elasticClient.Indices.Flush.WithIndex(indexName))
}
