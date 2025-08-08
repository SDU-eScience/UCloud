package foundation

import (
	"bytes"
	"encoding/json"
	"github.com/elastic/go-elasticsearch/v9/esapi"
	"github.com/elastic/go-elasticsearch/v9/typedapi/core/count"
	"github.com/elastic/go-elasticsearch/v9/typedapi/types"
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
	YYYYMMDD          = "2006.02.01"
	DAYS_TO_KEEP_DATA = 180
)

func pushLogsToElastic(event rpc.HttpCallLogEntry) {
	data, err := json.Marshal(event)
	if err != nil {
		log.Info("Failed to create json for event: ", event)
		return
	}
	dataSuffix := time.Now().UTC().Format(YYYYMMDD)
	indexName := "http_logs_" + event.RequestName + "-" + dataSuffix
	elasticClient.Index(indexName, bytes.NewReader(data))
}

// Delete old and expired entrieds
func CleanUpLogs() {
	list := GetListOfIndices()
	for _, index := range list {
		removeExpiredLogs(index)
		removeOldExpiredIndices()
	}
}

// Removes logs that has expired
func removeExpiredLogs(indexName string) {
	now := time.Now().UTC().Unix()
	nowAsString := strconv.FormatInt(now, 10)

	var buffer bytes.Buffer
	query := types.Query{
		Range: map[string]types.RangeQuery{
			"expiry": types.TermRangeQuery{Lt: &nowAsString},
		},
	}

	err := json.NewEncoder(&buffer).Encode(query)

	response, err := elasticClient.Count(
		elasticClient.Count.WithIndex(indexName),
		elasticClient.Count.WithBody(&buffer),
	)

	if err != nil {
		log.Info("Failed to count expired logs: ", err)
		return
	}

	var result map[string]interface{}
	err = json.NewDecoder(response.Body).Decode(&result)
	expiredCount := result["count"]

	query = types.Query{
		MatchAll: &types.MatchAllQuery{},
	}

	err = json.NewEncoder(&buffer).Encode(query)

	response, err = elasticClient.Count(
		elasticClient.Count.WithIndex(indexName),
		elasticClient.Count.WithBody(&buffer),
	)

	if err != nil {
		log.Info("Failed to count expired logs: ", err)
		return
	}

	err = json.NewDecoder(response.Body).Decode(&result)
	sizeOfIndex := result["count"]

	if expiredCount == 0 {
		log.Info("Nothing expired in index - moving on")
		return
	}

	if sizeOfIndex == expiredCount {
		log.Info("All docs are expired - faster to delete index")
		DeleteIndex(indexName)
	} else {
		query := types.Query{
			Range: map[string]types.RangeQuery{
				"expiry": types.TermRangeQuery{Lt: &nowAsString},
			},
		}

		err = json.NewEncoder(&buffer).Encode(query)

		body := strings.NewReader(buffer.String())
		response, err = elasticClient.DeleteByQuery(
			[]string{indexName},
			body,
		)

		if err != nil {
			log.Info("Failed to delete expired logs: ", err)
			return
		}

		FlushIndex(indexName)
	}

}

// Removes logs and any indices that have exceeded UCloud retainment period (DAYS_TO_KEEP_DATA)
func removeOldExpiredIndices() {}

func GetListOfIndices() []string {}

func DeleteIndex(indexName string) {
	if strings.Contains(indexName, "*") {
		log.Fatal("Cannot delete with wildcard. Index given " + indexName)
	}
	_, err := elasticClient.Indices.Delete([]string{indexName})
	if err != nil {
		log.Info("Failed to delete index: ", err)
	}
}

func FlushIndex(indexName string) {
	elasticClient.Indices.Flush(elasticClient.Indices.Flush.WithIndex(indexName))
}
