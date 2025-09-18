package launcher

type UCloudFrontend struct{}

func (uf *UCloudFrontend) Build(cb ComposeBuilder) {
	cb.Service(
		"frontend",
		"UCloud/Core: Frontend",
		Json{
			//language=json
			`
				{
					"image": "node",
					"command": ["sh", "-c", "npm install ; npm run start:compose"],
					"restart": "always",
					"hostname": "frontend",
					"working_dir": "/opt/ucloud",
					"volumes": [
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/frontend-web/webclient:/opt/ucloud"
				]
				}
			`,
		},
		true,
		true,
		false,
		"https://ucloud.localhost.direct",
		`
			Default credentials to access UCloud:
			
				Username: user<br>
				Password: mypassword<br>
		`,
	)
}
