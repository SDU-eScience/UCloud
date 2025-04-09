module ucloud.dk/shared

require (
	github.com/prometheus/client_golang v1.21.1
	github.com/jmoiron/sqlx v1.4.0
	github.com/lib/pq v1.10.9
	ucloud.dk/gonja/v2 v2.3.0
	github.com/golang-jwt/jwt/v5 v5.2.2
	github.com/anyascii/go v0.3.2
	gopkg.in/yaml.v3 v3.0.1
)

replace ucloud.dk/gonja/v2 => ../gonja

go 1.24
