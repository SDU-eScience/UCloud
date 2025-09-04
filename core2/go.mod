module ucloud.dk/core

require (
	github.com/blevesearch/bleve v1.0.14
	github.com/golang-jwt/jwt/v5 v5.2.2
	github.com/gorilla/websocket v1.5.3
	github.com/hashicorp/golang-lru/v2 v2.0.7
	github.com/pquerna/otp v1.4.0
	golang.org/x/crypto v0.37.0
	golang.org/x/exp v0.0.0-20240404231335-c0f41cb1a7a0
	golang.org/x/image v0.29.0
	gopkg.in/gomail.v2 v2.0.0-20160411212932-81ebce5c23df
	gopkg.in/yaml.v3 v3.0.1
	ucloud.dk/gonja/v2 v2.3.0
	ucloud.dk/shared v1.0.0
)

replace ucloud.dk/gonja/v2 => ../provider-integration/gonja

replace ucloud.dk/shared => ../provider-integration/shared

require (
	github.com/RoaringBitmap/roaring v0.4.23 // indirect
	github.com/anyascii/go v0.3.2 // indirect
	github.com/beorn7/perks v1.0.1 // indirect
	github.com/blevesearch/go-porterstemmer v1.0.3 // indirect
	github.com/blevesearch/mmap-go v1.0.2 // indirect
	github.com/blevesearch/segment v0.9.0 // indirect
	github.com/blevesearch/snowballstem v0.9.0 // indirect
	github.com/blevesearch/zap/v11 v11.0.14 // indirect
	github.com/blevesearch/zap/v12 v12.0.14 // indirect
	github.com/blevesearch/zap/v13 v13.0.6 // indirect
	github.com/blevesearch/zap/v14 v14.0.5 // indirect
	github.com/blevesearch/zap/v15 v15.0.3 // indirect
	github.com/boombuler/barcode v1.0.1-0.20190219062509-6c824513bacc // indirect
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/couchbase/vellum v1.0.2 // indirect
	github.com/dustin/go-humanize v1.0.1 // indirect
	github.com/glycerine/go-unsnap-stream v0.0.0-20181221182339-f9677308dec2 // indirect
	github.com/golang/protobuf v1.5.0 // indirect
	github.com/golang/snappy v0.0.1 // indirect
	github.com/jmoiron/sqlx v1.4.0 // indirect
	github.com/json-iterator/go v1.1.12 // indirect
	github.com/lib/pq v1.10.9 // indirect
	github.com/modern-go/concurrent v0.0.0-20180306012644-bacd9c7ef1dd // indirect
	github.com/modern-go/reflect2 v1.0.2 // indirect
	github.com/mschoch/smat v0.2.0 // indirect
	github.com/munnerz/goautoneg v0.0.0-20191010083416-a7dc8b61c822 // indirect
	github.com/philhofer/fwd v1.0.0 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/prometheus/client_golang v1.21.1 // indirect
	github.com/prometheus/client_model v0.6.1 // indirect
	github.com/prometheus/common v0.62.0 // indirect
	github.com/prometheus/procfs v0.15.1 // indirect
	github.com/sirupsen/logrus v1.9.3 // indirect
	github.com/steveyen/gtreap v0.1.0 // indirect
	github.com/tinylib/msgp v1.1.0 // indirect
	github.com/willf/bitset v1.1.10 // indirect
	go.etcd.io/bbolt v1.3.5 // indirect
	golang.org/x/sys v0.32.0 // indirect
	golang.org/x/text v0.27.0 // indirect
	google.golang.org/protobuf v1.36.1 // indirect
	gopkg.in/alexcesaro/quotedprintable.v3 v3.0.0-20150716171945-2caba252f4dc // indirect
)

go 1.24
