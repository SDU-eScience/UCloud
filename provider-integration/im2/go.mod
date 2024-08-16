module ucloud.dk

require (
	atomicgo.dev/keyboard v0.2.9
	github.com/anyascii/go v0.3.2
	github.com/envoyproxy/go-control-plane v0.12.0
	github.com/fergusstrange/embedded-postgres v1.27.0
	github.com/golang-jwt/jwt/v5 v5.2.1
	github.com/golang/protobuf v1.5.4
	github.com/gorilla/websocket v1.5.3
	github.com/hashicorp/golang-lru/v2 v2.0.7
	github.com/jmoiron/sqlx v1.4.0
	github.com/lib/pq v1.10.9
	github.com/prometheus/client_golang v1.19.1
	google.golang.org/grpc v1.65.0
	google.golang.org/protobuf v1.34.1
	gopkg.in/yaml.v3 v3.0.1
	ucloud.dk/gonja/v2 v2.3.0
)

replace ucloud.dk/gonja/v2 => ../gonja

require (
	cel.dev/expr v0.15.0 // indirect
	github.com/beorn7/perks v1.0.1 // indirect
	github.com/census-instrumentation/opencensus-proto v0.4.1 // indirect
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/cncf/xds/go v0.0.0-20240423153145-555b57ec207b // indirect
	github.com/containerd/console v1.0.3 // indirect
	github.com/dustin/go-humanize v1.0.1 // indirect
	github.com/envoyproxy/protoc-gen-validate v1.0.4 // indirect
	github.com/json-iterator/go v1.1.12 // indirect
	github.com/kr/text v0.2.0 // indirect
	github.com/modern-go/concurrent v0.0.0-20180306012644-bacd9c7ef1dd // indirect
	github.com/modern-go/reflect2 v1.0.2 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/prometheus/client_model v0.5.0 // indirect
	github.com/prometheus/common v0.48.0 // indirect
	github.com/prometheus/procfs v0.12.0 // indirect
	github.com/sirupsen/logrus v1.9.3 // indirect
	github.com/xi2/xz v0.0.0-20171230120015-48954b6210f8 // indirect
	golang.org/x/exp v0.0.0-20240404231335-c0f41cb1a7a0 // indirect
	golang.org/x/net v0.25.0 // indirect
	golang.org/x/sys v0.20.0 // indirect
	golang.org/x/text v0.15.0 // indirect
	google.golang.org/genproto/googleapis/api v0.0.0-20240528184218-531527333157 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20240528184218-531527333157 // indirect
)

go 1.21
