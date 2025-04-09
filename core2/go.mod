module ucloud.dk/core

require (
	gopkg.in/yaml.v3 v3.0.1
	ucloud.dk/shared v1.0.0
)

replace ucloud.dk/gonja/v2 => ../provider-integration/gonja
replace ucloud.dk/shared => ../provider-integration/shared


require (
	github.com/anyascii/go v0.3.2 // indirect
	github.com/beorn7/perks v1.0.1 // indirect
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/golang-jwt/jwt/v5 v5.2.2 // indirect
	github.com/jmoiron/sqlx v1.4.0 // indirect
	github.com/kr/text v0.2.0 // indirect
	github.com/lib/pq v1.10.9 // indirect
	github.com/munnerz/goautoneg v0.0.0-20191010083416-a7dc8b61c822 // indirect
	github.com/prometheus/client_golang v1.21.1 // indirect
	github.com/prometheus/client_model v0.6.1 // indirect
	github.com/prometheus/common v0.62.0 // indirect
	github.com/prometheus/procfs v0.15.1 // indirect
	golang.org/x/sys v0.28.0 // indirect
	google.golang.org/protobuf v1.36.1 // indirect
)

go 1.24
