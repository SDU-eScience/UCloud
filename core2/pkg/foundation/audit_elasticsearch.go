package foundation

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/elastic/go-elasticsearch/v9"
	"ucloud.dk/core/pkg/config"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var elasticClient *elasticsearch.Client
var elasticConfig rpc.ElasticConfig
var elasticHost *config.HostInfo

type elasticIndexRequest struct {
	index    string
	document []byte
}

func InitAuditElasticSearch(conf rpc.ElasticConfig) func(event rpc.HttpCallLogEntry) {
	var err error
	elasticConfig = conf
	elasticHost = &config.HostInfo{
		Address: conf.Address,
		Port:    conf.Port,
		Scheme:  conf.Scheme,
	}
	if elasticHost.Address == "" {
		panic("InitAuditElasticSearch was called without proper configuration")
	}
	cfg := elasticsearch.Config{
		Addresses: []string{elasticHost.ToURL()},
		Transport: http.DefaultTransport,
		Username:  conf.Credentials.Username,
		Password:  conf.Credentials.Password,
	}

	elasticClient, err = elasticsearch.NewClient(cfg)

	if err != nil {
		panic(fmt.Sprintf("Could not construct elasticsearch client: %v", err))
	}

	go elasticAuditChannelProcessor()

	return func(event rpc.HttpCallLogEntry) {
		data, err := json.Marshal(event)
		if err != nil {
			log.Info("Failed to create json for event: ", event)
			return
		}
		dateSuffix := time.Now().UTC().Format(elasticDateFormat)
		indexName := "http_logs_" + event.RequestName + "-" + dateSuffix
		elasticAuditChannel <- elasticIndexRequest{index: indexName, document: data}
	}
}

type elasticBulkIndexOp struct {
	Index struct {
		Name string `json:"_index"`
		Id   string `json:"_id,omitempty"`
	} `json:"index"`
}

var elasticAuditChannel = make(chan elasticIndexRequest, 1024*4)

func elasticAuditChannelProcessor() {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	indexer := &elasticBulkIndexer{}

	for {
		select {
		case request := <-elasticAuditChannel:
			err := indexer.IndexDoc(request.index, request.document)
			if err != nil {
				log.Info("could not insert documents into elastic search: %s", err)
			}

		case _ = <-ticker.C:
			err := indexer.Flush()
			if err != nil {
				log.Info("could not insert documents into elastic search: %s", err)
			}
		}
	}
}

type elasticBulkIndexer struct {
	builder  strings.Builder
	docCount int
}

func (b *elasticBulkIndexer) IndexDoc(indexName string, doc []byte) error {
	op := elasticBulkIndexOp{}
	op.Index.Name = indexName
	opBytes, _ := json.Marshal(op)

	b.builder.Write(opBytes)
	b.builder.WriteString("\n")

	b.builder.Write(doc)
	b.builder.WriteString("\n")

	b.docCount++
	if b.docCount >= 250 {
		return b.Flush()
	} else {
		return nil
	}
}

func (b *elasticBulkIndexer) payload() []byte {
	return []byte(b.builder.String())
}

func (b *elasticBulkIndexer) Flush() error {
	if b.docCount == 0 {
		return nil
	}

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	requestUrl, _ := url.Parse(fmt.Sprintf("%s/_bulk", elasticHost.ToURL()))

	httpRequest := http.Request{
		Method: "POST",
		URL:    requestUrl,
		Body:   io.NopCloser(bytes.NewReader(b.payload())),
		Header: header,
	}
	_, err := elasticClient.BaseClient.Transport.Perform(
		&httpRequest,
	)

	if err != nil {
		return err
	} else {
		b.builder.Reset()
		b.docCount = 0
		return nil
	}
}

const (
	elasticDateFormat       = "2006.01.02"
	elasticMonthFormat      = "2006.01"
	elasticDaysToKeepData   = 180
	grafanaElasticAliasDays = 7
)

// Daily cleanup
// =====================================================================================================================
// The ElasticDailyCleanup function is responsible for daily cleanup and optimization of ElasticSearch indexes. This includes
// the following high-level tasks:
//
// - Remove logs that are too old (see expiry and elasticDaysToKeepData)
// - Shrink indexes to optimize space
// - Reindex old logs into an index for a full month

func ElasticDailyCleanup() {
	log.Debug("Cleaning up old logs")
	httpLogsList := elasticGetIndexTitles([]string{"http_logs*"})
	now := time.Now().UTC()

	// Remove logs that have expiry field matching expiry < now
	log.Debug("Cleaning up expired logs")
	for _, index := range httpLogsList {
		elasticRemoveExpiredLogs(index)
	}

	// Remove all log indices that are older than elasticDaysToKeepData
	log.Debug("Cleaned up expired indices")
	expiredDate := now.AddDate(0, 0, -elasticDaysToKeepData).Format(elasticDateFormat)
	expiredLogs := elasticGetIndexTitles([]string{"*-" + expiredDate})
	for _, expiredLog := range expiredLogs {
		elasticDeleteIndex(expiredLog)
	}

	// Shink yesterday's indices so they only have 1 shard usage
	log.Debug("Shrinking indices")
	yesterdayDateFormat := now.AddDate(0, 0, -1).Format(elasticDateFormat)
	yesterdayIndices := elasticGetIndexTitles([]string{"*-" + yesterdayDateFormat})
	for _, yesterdayLog := range yesterdayIndices {
		elasticShrink(yesterdayLog)
	}

	// Reindex last weeks log indices into a monthly index with format YYYYMM
	log.Debug("Reindexing indices")
	daysAgo := time.Now().AddDate(0, 0, -grafanaElasticAliasDays-1).UTC().Format(elasticDateFormat)
	reindexLogs := elasticGetIndexTitles([]string{"*-" + daysAgo})
	for _, reindexLog := range reindexLogs {
		elasticReindexToMonthly(reindexLog)
	}
	log.Info("Cleanup complete")
}

// Daily creation of Aliases
// =======================================================================================================
//	The ElasticDailyCreateAliasesForGrafana function creates a "grafana" alias for http_logs for
//	each index the last grafanaElasticAliasDays into the past

func ElasticDailyCreateAliasesForGrafana() {
	//Delete old grafana aliases
	log.Debug("Deleting old grafana aliases")
	grafanaLogs := elasticGetIndexTitles([]string{"grafana"})
	_, err := elasticClient.Indices.DeleteAlias(
		grafanaLogs,
		[]string{"grafana"},
	)
	if err != nil {
		log.Info("Failed to delete old grafana aliases")
		return
	}

	//Create new for each index within the time limit
	log.Debug("Creating new grafana aliases")
	for i := range grafanaElasticAliasDays {
		dateSuffix := time.Now().AddDate(0, 0, -i).UTC().Format(elasticDateFormat)
		logs := elasticGetIndexTitles([]string{"http_logs*" + dateSuffix + "*"})
		_, err := elasticClient.Indices.PutAlias(
			logs,
			"grafana",
		)
		if err != nil {
			log.Info("Failed to create grafana alias for logs: '%s' \n Error: %s", logs, err)
			return
		}
	}
	log.Debug("Done creating new grafana aliases")
}

// elasticRemoveExpiredLogs Removes log entries that has exceeded set expiry field in given index
func elasticRemoveExpiredLogs(indexName string) {
	now := time.Now().UTC().Unix()

	expiredQuery := map[string]any{
		"query": map[string]any{
			"range": map[string]any{
				"expiry": map[string]any{
					"lte": now,
				},
			},
		},
	}

	queryBytes, _ := json.Marshal(expiredQuery)

	expiredCount := elasticCountDocs(indexName, string(queryBytes))
	sizeOfIndex := elasticCountDocs(indexName, "")

	if expiredCount == 0 {
		log.Debug("Nothing expired in index: '%s' - moving on", indexName)
		return
	}
	if sizeOfIndex == expiredCount {
		log.Debug("All docs are expired - faster to delete index %s ", indexName)
		elasticDeleteIndex(indexName)
	} else {
		_, err := elasticClient.DeleteByQuery(
			[]string{indexName},
			strings.NewReader(string(queryBytes)),
		)

		if err != nil {
			log.Info("Failed to delete expired logs for '%s': Error: %s", indexName, err)
			return
		}

		elasticFlushIndex(indexName)
	}
}

// REINDEX OPERATIONS
// ==============================================================================================
// To make sure to keep our elastic cluster fully operational and fast responding
// we need to reduce the number of shards that are used. This can be achieved by reindexing (moving
// documents from one index into another index)
// By reindexing each daily http_log index to a monthly index for that specific request type
// we can reduce the number of shards used from 30 to 1. This should only happen with
// indices that are considered cold data.
// Once the source index has been moved to the target index, then we can delete the old indices to
// release the space and shards used.

// The elasticReindexToMonthly function merges a daily index into the monthly index
func elasticReindexToMonthly(indexName string) {
	monthFormat := time.Now().AddDate(0, 0, -grafanaElasticAliasDays-1).UTC().Format(elasticMonthFormat)
	if elasticCountDocs(indexName, "") == 0 {
		elasticDeleteIndex(indexName)
		return
	}
	toIndex := strings.Split(indexName, "-")[0] + "-" + monthFormat
	reindex(indexName, toIndex)
}

func reindex(fromIndex string, toIndex string) {
	if !elasticIndexExists(toIndex) {
		elasticCreateIndex(toIndex)
	}

	request := map[string]any{
		"source": map[string]any{
			"index": fromIndex,
			"size":  2500,
		},
		"dest": map[string]any{
			"index": toIndex,
		},
	}

	requestBytes, _ := json.Marshal(request)

	_, err := elasticClient.Reindex(bytes.NewReader(requestBytes))

	if err != nil {
		log.Info("Error on reindex: %s \n", err)
		return
	}

	// Make sure all documents have been moved
	fromCount := elasticCountDocs(fromIndex, "")
	toCount := elasticCountDocs(toIndex, "")
	for toCount < fromCount {
		log.Info("Waiting for reindex to complete. Working on %v -> %v (CountFrom: %v, CountTo: %v )", fromIndex, toIndex, fromCount, toCount)
		time.Sleep(time.Second * 10)
		toCount = elasticCountDocs(toIndex, "")
	}

	// Delete old index
	elasticDeleteIndex(fromIndex)

}

// Shrinking operations
// ==============================================================================================
// We need to decrease our usage of shards in the Elastic cluster.
// When searching in the elastic cluster each shard has to be searched and since
// we have a daily index for each http request type for each day, and it is being
// saved with 2 shards for primary and 2 shards for replication are we easily creating
// many hundred shards every day.
// To handle this we shrink the indices of the audit from yesterday to a single primary and
// a single replica shard. (4 => 2 shards per index)
// To shrink an index we do the following:
// 1. Prepare the index to be shrinked
// 		Choose a single cluster node to hold all shards of the source index
// 		Make the source index write only make it unavailable to the cluster for searches
// 2. Wait for the move of shards to be complete
// 3. Define the settings of the target index and do the actual shrinking in moving data to the smaller index
// 4. Checks if the _small and the original index have the same number of documents. If they do
// 		Its a sign that all data have been moved, and we can delete the old index. If not we keep the old index
//		just to be sure that we do not lose data.

// The elasticShrink function is reducing the number of shards used by an index. If the index
// already is at 1 primary shard then we do not need to shrink it.
func elasticShrink(indexName string) {
	targetIndex := indexName + "_small"
	log.Debug("Shrinking index: %s", indexName)
	if elasticGetShardCount(indexName) == 1 {
		log.Debug("Index is already at 1 shard")
		return
	}
	elasticPrepareSourceIndex(indexName)
	elasticWaitForRelocation(indexName)
	elasticShrinkIndex(indexName, targetIndex)
	if elasticIndexIsSameSize(indexName, targetIndex) {
		elasticDeleteIndex(indexName)
	}
}

// The elasticPrepareSourceIndex function pushes the new settings of the source index.
// - Index shards should be gathered on a specific data node of the cluster
// - Make the source index write only make it unavailable to the cluster for searches
func elasticPrepareSourceIndex(indexName string) {
	request := map[string]any{
		"index": map[string]any{
			"routing": map[string]any{
				"allocation": map[string]any{
					"require": map[string]any{
						"_name": elasticConfig.GatherNode,
					},
				},
			},
			"blocks": map[string]any{
				"write": true,
			},
		},
	}

	requestBytes, _ := json.Marshal(request)

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	requestUrl, _ := url.Parse(fmt.Sprintf("%s/%s/_settings", elasticHost.ToURL(), indexName))

	retries := 0
	for retries < 3 {
		httpRequest := http.Request{
			Method: "PUT",
			URL:    requestUrl,
			Body:   io.NopCloser(bytes.NewReader(requestBytes)),
			Header: header,
		}
		_, err := elasticClient.BaseClient.Transport.Perform(
			&httpRequest,
		)
		if err != nil {
			log.Debug("Failed to perform http request. Error: %s", err)
			time.Sleep(5 * time.Second)
			retries++
		} else {
			return
		}
	}
}

// elasticWaitForRelocation checks the cluster health to see if we still are relocating.
// Relocating is the operation of moving the original shards to the node chosen for gathering.
// This is more or less instant in most case since our http_logs are not huge amounts of data
func elasticWaitForRelocation(indexName string) {
	counter := 0
	for elasticGetClusterHealth().RelocatingShards > 0 {
		if counter%10 == 0 {
			log.Debug("Waiting for relocation of %s to complete", indexName)
		}
		counter++
		time.Sleep(time.Second * 1)
	}
}

// The function shrinkIndex describes the settings of the target index.
// Defines
//   - Number of shards used for primary and replicas
//   - That shards are allowed on all data nodes
//   - That the index is not write only
//   - Which compression method to use. We choose best_compression which uses ZSTD.
//     This gives us lower usage consumption but might give higher search time.
//
// After defining the settings it does the actual moving of data to the http_logs_REQUESTNAME_small
func elasticShrinkIndex(indexName string, targetIndexName string) {
	request := map[string]any{
		"settings": map[string]any{
			"index": map[string]any{
				"number_of_shards": 1,
				"blocks": map[string]any{
					"write": false,
				},
				"number_of_replicas": 1,
				"codec":              "best_compression",
				"routing": map[string]any{
					"allocation": map[string]any{
						"require": map[string]any{
							"_name": "*",
						},
					},
				},
			},
		},
	}

	requestBytes, _ := json.Marshal(request)

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	requestUrl, _ := url.Parse(fmt.Sprintf("%s/%s/_shrink/%s", elasticHost.ToURL(), indexName, targetIndexName))

	retries := 0
	for retries < 3 {
		httpRequest := http.Request{
			Method: "POST",
			URL:    requestUrl,
			Body:   io.NopCloser(bytes.NewReader(requestBytes)),
			Header: header,
		}
		_, err := elasticClient.BaseClient.Transport.Perform(
			&httpRequest,
		)
		if err != nil {
			log.Debug("Failed to perform shrink request. Error: %s ", err)
			time.Sleep(5 * time.Second)
			retries++
		} else {
			return
		}
	}
}

// Utility Functions
// Collection of requests for cluster health, retrieval of data and comparisons

func elasticGetClusterHealth() ElasticHealthResponse {
	resp, err := elasticClient.Cluster.Health()
	if err != nil {
		log.Info("Failed to get cluster health: ", err)
		return ElasticHealthResponse{}
	} else {
		defer util.SilentClose(resp.Body)
		bytesRead, _ := io.ReadAll(resp.Body)
		value := ElasticHealthResponse{}
		_ = json.Unmarshal(bytesRead, &value)
		return value
	}
}

// elasticGetIndexTitles retrieves a list of indices matching the input indicesToFind. Wildcards are accepted e.g. "htto_logs*"
func elasticGetIndexTitles(indicesToFind []string) []string {
	resp, err := elasticClient.Indices.Get(
		indicesToFind,
		elasticClient.Indices.Get.WithAllowNoIndices(true),
	)
	if err != nil {
		log.Info("Failed to get indices. Error: %s", err)
		return nil
	}

	var result map[string]interface{}

	defer util.SilentClose(resp.Body)
	bytesRead, err := io.ReadAll(resp.Body)
	if err == nil {
		errs := json.Unmarshal(bytesRead, &result)

		if errs != nil {
			log.Info("Failed to unmarshal indices list. Error: %s", errs)
			return nil // TODO(Dan): I don't understand this code
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

// elasticIndexIsSameSize compare document count between firstIndexName and secondIndexName
func elasticIndexIsSameSize(firstIndexName string, secondIndexName string) bool {
	return elasticCountDocs(firstIndexName, "") == elasticCountDocs(secondIndexName, "")
}

// elasticGetShardCount gets number of shard for given index
func elasticGetShardCount(indexName string) int {
	resp, err := elasticClient.Indices.GetSettings(
		elasticClient.Indices.GetSettings.WithIndex(indexName),
	)
	if err != nil {
		log.Info("Failed to get shard Count: ", err)
		return 0
	}
	var result struct {
		Settings struct {
			Index struct {
				NumberOfShards int `json:"number_of_shards"`
			} `json:"index"`
		} `json:"settings"`
	}

	defer util.SilentClose(resp.Body)
	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Error("Failed to read body of shard count request. Error: ", err)
		return 0
	}
	_ = json.Unmarshal(respBytes, &result)
	return result.Settings.Index.NumberOfShards
}

// elasticCountDocs : returns number of documents in index (indexName).
// If no query is given it is a matchAll
func elasticCountDocs(indexName string, query string) int {
	ioReader := strings.NewReader(query)
	response, err := elasticClient.Count(
		elasticClient.Count.WithIndex(indexName),
		elasticClient.Count.WithBody(ioReader),
	)

	if err != nil {
		log.Info("Failed to count expired logs: ", err)
		return 0
	}

	defer util.SilentClose(response.Body)
	readBytes, err := io.ReadAll(response.Body)
	value := ElasticCountResponse{}
	_ = json.Unmarshal(readBytes, &value)
	return value.Count
}

// elasticDeleteIndex : Deletes entire given index
func elasticDeleteIndex(indexName string) {
	if strings.Contains(indexName, "*") {
		log.Error("Cannot delete with wildcard. Index given %s", indexName)
		return
	}
	_, err := elasticClient.Indices.Delete([]string{indexName})
	if err != nil {
		log.Info("Failed to delete index: %s .  Error %s ", indexName, err)
	}
}

func elasticCreateIndex(indexName string) {
	normalizedIndexName := strings.ToLower(indexName)
	if elasticIndexExists(normalizedIndexName) {
		return
	}
	body := map[string]any{
		"settings": map[string]any{
			"number_of_shards":   1,
			"number_of_replicas": 1,
		},
	}
	request, _ := json.Marshal(body)

	resp, err := elasticClient.Indices.Create(
		normalizedIndexName,
		elasticClient.Indices.Create.WithBody(bytes.NewReader(request)),
	)
	if err != nil {
		log.Debug("Creation of index %s failed. \n Error: %s \n Respone: %s", indexName, err, resp)
	}
}

// IndexExists Check if the given index exists in the cluster
func elasticIndexExists(indexName string) bool {
	resp, _ := elasticClient.Indices.Exists(
		[]string{indexName},
	)
	return resp.StatusCode == http.StatusOK
}

// FlushIndex Flushes the given index to force changes
func elasticFlushIndex(indexName string) {
	elasticClient.Indices.Flush(elasticClient.Indices.Flush.WithIndex(indexName))
}
