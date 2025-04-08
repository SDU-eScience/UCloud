package launcher

import "strconv"

type UCloudBackend struct{}

func (uc *UCloudBackend) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	logs := NewFile(dataDir).Child("logs", true)
	homeDir := NewFile(dataDir).Child("backend-home", true)
	configDir := NewFile(dataDir).Child("backend-config", true)
	gradleDir := NewFile(dataDir).Child("backend-gradle", true)
	postgresDataDir := NewFile(dataDir).Child("pg-data", true)

	cb.Service(
		"backend",
		"UCloud/Core: Backend",
		Json{
			//language=json
			`
				{
					"image": "` + imDevImage + `",
					"command": ["sleep", "inf"],
					"restart": "always",
					"hostname": "backend",
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(8080)) + `:8080",
						"` + strconv.Itoa(portAllocator.Allocate(11412)) + `:11412",
						"` + strconv.Itoa(portAllocator.Allocate(51231)) + `:51231"
					],
					"volumes": [
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/backend:/opt/ucloud",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/frontend-web/webclient:/opt/frontend",
						"` + logs.GetAbsolutePath() + `:/var/log/ucloud",
						"` + configDir.GetAbsolutePath() + `:/etc/ucloud",
						"` + homeDir.GetAbsolutePath() + `:/home",
						"` + gradleDir.GetAbsolutePath() + `:/root/.gradle"
					]
				}
		`},
		true,
		true,
		true,
		"",
		"",
	)

	cb.Service(
		"postgres",
		"UCloud/Core: Postgres",
		Json{
			//language=json
			`
			{
				"image": "postgres:15.0",
				"hostname": "postgres",
				"restart": "always",
				"environment":{
					"POSTGRES_PASSWORD": "postgrespassword"
				},
				"volumes": [
					"` + postgresDataDir.GetAbsolutePath() + `:/var/lib/postgresql/data",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/backend:/opt/ucloud"
				],
				"ports": [
					"` + strconv.Itoa(portAllocator.Allocate(35432)) + `:5432"
				]
			}`,
		},
		true,
		true,
		false,
		"",
		"",
	)

	cb.Service(
		"pgweb",
		"UCloud/Core: Postgres UI",
		Json{
			//language=json
			`
			{
				"image": "sosedoff/pgweb",
				"hostname": "pgweb",
				"restart": "always",
				"environment": {
					"PGWEB_DATABASE_URL": "postgres://postgres:postgrespassword@postgres:5432/postgres?sslmode=disable"
				}
			}`,
		},
		true,
		true,
		false,
		"https://postgres.localhost.direct",
		`
			The postgres interface is connected to the database of UCloud/Core. You don't need any credentials. 
		
			If you wish to connect via psql or some tool:
		
			Hostname: localhost<br>
			Port: 35432<br>
			Database: postgres<br>
			Username: postgres<br>
			Password: postgrespassword
		`,
	)

	redisDataDir := NewFile(dataDir).Child("redis-data", true)
	cb.Service(
		"redis",
		"UCloud/Core: Redis",
		Json{
			//language=json
			`
			{
				"image": "redis:5.0.9",
				"hostname": "redis",
				"restart": "always",
				"volumes": [
					"` + redisDataDir.GetAbsolutePath() + `:/data"
					]
			}
			`,
		},
		true,
		true,
		false,
		"",
		"",
	)
}
