package fsearch

import (
	"bytes"
	_ "embed"
	"github.com/bits-and-blooms/bloom/v3"
	"github.com/sugarme/tokenizer"
	"hash/fnv"
	"path/filepath"
	"strings"
	"sync"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
	"unicode/utf8"
)

func hugTokenize(input string) []string {
	// NOTE(Dan): The library is known to crash on invalid UTF8
	if utf8.ValidString(input) {
		toks, _ := tk.Tokenize(input)
		return toks
	} else {
		return nil
	}
}

type SearchQuery struct {
	normQuery string
	tokens    []string
}

func (q *SearchQuery) Matches(path string) bool {
	return strings.Contains(strings.ToLower(filepath.Base(path)), q.normQuery)
}

func NewQuery(query string) SearchQuery {
	return SearchQuery{
		normQuery: strings.ToLower(query),
		tokens:    hugTokenize(query),
	}
}

type FileInfo struct {
	Parent string
	Name   string
	Size   int64
}

type SearchIndex struct {
	BucketCount int
	Buckets     []SearchBucket
}

type SearchBucket struct {
	Name                *bloom.BloomFilter
	exactSetForBuilding map[string]util.Empty
}

var tk *tokenizer.Tokenizer
var initOnce sync.Once

//go:embed tokenizer/tokenizer.json
var tokenizerData []byte

func Init() {
	initOnce.Do(func() {
		tok, err := TokenizerFromData(tokenizerData)
		if err != nil {
			panic(err)
		}

		tk = tok
	})
}

const maxBucketSize = 1024 * 16

func NewIndexBuilder(bucketCount int) *SearchIndex {
	result := &SearchIndex{
		BucketCount: bucketCount,
	}

	for i := 0; i < bucketCount; i++ {
		result.Buckets = append(result.Buckets, SearchBucket{
			Name:                bloom.NewWithEstimates(maxBucketSize, 0.01),
			exactSetForBuilding: make(map[string]util.Empty),
		})
	}

	return result
}

func LoadIndex(data []byte) *SearchIndex {
	result := &SearchIndex{}
	var err error

	buf := util.NewBuffer(bytes.NewBuffer(data))
	bucketCount := int(buf.ReadU32())
	result.BucketCount = bucketCount

	for i := 0; i < bucketCount; i++ {
		nameBinLen := buf.ReadU32()
		nameBin := buf.ReadNext(int(nameBinLen))

		name := &bloom.BloomFilter{}
		err = name.UnmarshalBinary(nameBin)
		if err != nil {
			break
		}

		result.Buckets = append(result.Buckets, SearchBucket{
			Name: name,
		})
	}

	if err != nil {
		log.Warn("Failed to load index: %s", err)
		return NewIndexBuilder(1024 * 16)
	}

	return result
}

func (s *SearchIndex) BuilderAvgBucketSize() float64 {
	acc := 0
	for _, bucket := range s.Buckets {
		acc += len(bucket.exactSetForBuilding)
	}

	return float64(acc) / float64(s.BucketCount)
}

func (s *SearchIndex) Marshal() []byte {
	rawBuf := &bytes.Buffer{}
	buf := util.NewBufferWithWriter(rawBuf)
	buf.WriteU32(uint32(s.BucketCount))
	for _, bucket := range s.Buckets {
		if len(bucket.exactSetForBuilding) < maxBucketSize {
			// Re-encode the set with exact estimates
			bucket.Name = bloom.NewWithEstimates(uint(len(bucket.exactSetForBuilding)), 0.01)
			for tok, _ := range bucket.exactSetForBuilding {
				bucket.Name.AddString(tok)
			}
		}

		nameBin, _ := bucket.Name.MarshalBinary()
		buf.WriteU32(uint32(len(nameBin)))
		buf.WriteBytes(nameBin)
	}

	return rawBuf.Bytes()
}

type PreparedFileInfo struct {
	nameTokens       []string
	parentComponents []string
}

func (f *FileInfo) Prepare() PreparedFileInfo {
	nameTokens := hugTokenize(f.Name)
	components := util.Components(f.Parent)
	return PreparedFileInfo{
		nameTokens:       nameTokens,
		parentComponents: components,
	}
}

func (s *SearchIndex) Append(info PreparedFileInfo) {
	if s.BucketCount == 0 {
		return
	}

	for i := 0; i < len(info.parentComponents); i++ {
		ancestor := "/" + strings.Join(info.parentComponents[:i+1], "/")
		bucket := &s.Buckets[hash(s.BucketCount, ancestor)]

		for _, token := range info.nameTokens {
			bucket.Name.AddString(token)

			if len(bucket.exactSetForBuilding) < maxBucketSize {
				bucket.exactSetForBuilding[token] = util.Empty{}
			}
		}
	}
}

func hash(count int, input string) int {
	if count == 0 {
		return 0
	}

	h := fnv.New32a()
	_, _ = h.Write([]byte(input))

	return int(h.Sum32()) % count
}

func (s *SearchIndex) ContinueDown(dir string, query SearchQuery) bool {
	bucket := &s.Buckets[hash(s.BucketCount, dir)]

	threshold := len(query.tokens)
	matches := 0

	for _, token := range query.tokens {
		if bucket.Name.Test([]byte(token)) {
			matches++
		}
	}

	return matches >= threshold
}
