package launcher

import (
	"fmt"
	"strconv"
)

type GateWay struct {
	didAppendInstall bool
}

func (gw *GateWay) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	gatewayDir := NewFile(dataDir).Child("gateway", true)
	gatewayData := gatewayDir.Child("data", true)
	certificates := gatewayDir.Child("certs", true)

	cert := certificates.Child("tls.crt", false)
	key := certificates.Child("tls.key", false)

	if len(readLines(cert.GetAbsolutePath())) == 0 || len(readLines(key.GetAbsolutePath())) == 0 && !gw.didAppendInstall {
		gw.didAppendInstall = true
		PostExecFile.WriteString("\n " + repoRoot.GetAbsolutePath() + "/launcher install-certs\n\n")
	}

	core2Config := ""
	if UseCore2Experimental() {
		core2Config = TrimIndent(`
			reverse_proxy /api/* core2:8080
			reverse_proxy /auth/* core2:8080
		`)
	} else if UseCore2() {
		core2Config = TrimIndent(`
			reverse_proxy /api/avatar/* core2:8080
			reverse_proxy /auth/* backend:8080
		`)
	} else {
		core2Config = TrimIndent(`
			reverse_proxy /auth/* backend:8080
		`)
	}

	gatewayConfig := gatewayDir.Child("Caddyfile", false)
	gatewayConfig.WriteText(
		TrimIndent(fmt.Sprintf(`
			{
				order grpc_web before reverse_proxy
			}
			
			https://ucloud.localhost.direct {
				grpc_web
				%s
				reverse_proxy /api/auth-callback-csrf frontend:9000
				reverse_proxy /api/auth-callback frontend:9000
				reverse_proxy /api/sync-callback frontend:9000
				reverse_proxy /assets frontend:9000
				reverse_proxy /favicon.ico frontend:9000
				reverse_proxy /favicon.svg frontend:9000
				reverse_proxy /AppVersion.txt frontend:9000
				reverse_proxy /Images/* frontend:9000
				reverse_proxy /app frontend:9000
				reverse_proxy /app/* frontend:9000
				reverse_proxy /@* frontend:9000
				reverse_proxy /node_modules/* frontend:9000
				reverse_proxy /site.config.json frontend:9000
				reverse_proxy /api/* backend:8080
				reverse_proxy / frontend:9000
				reverse_proxy /avatar.AvatarService/* h2c://backend:11412

				header {
					Cross-Origin-Opener-Policy "same-origin"
					Cross-Origin-Embedder-Policy "require-corp"
				}
			}

			https://postgres.localhost.direct {
				reverse_proxy pgweb:8081
			}

			https://k8.localhost.direct {
				reverse_proxy k8:8889
			}

			https://k8-pg.localhost.direct {
				reverse_proxy k8pgweb:8081
			}

			https://slurm.localhost.direct {
				reverse_proxy slurm:8889
			}

			https://go-slurm.localhost.direct {
				reverse_proxy go-slurm:8889
			}

			https://go-k8s.localhost.direct {
				reverse_proxy gok8s:8889
			}

			https://slurm-pg.localhost.direct {
				reverse_proxy slurmpgweb:8081
			}

		  	https://go-k8s-metrics.localhost.direct {
				reverse_proxy gok8s:7867
			}

			https://ipa.localhost.direct {
				handle / {
					redir https://ipa.localhost.direct/ipa/ui/
				}

				handle {
					reverse_proxy https://free-ipa {
						header_up Host ipa.ucloud
						header_up Referer "https://ipa.ucloud{uri}"

						transport http {
							tls
							tls_insecure_skip_verify
						}
					}
				}
			}

			*.localhost.direct {
				tls /certs/tls.crt /certs/tls.key

				@k8apps {
					header_regexp k8app Host ^k8-.*
				}
				reverse_proxy @k8apps k8:8889

				@slurmapps {
					header_regexp slurmapp Host ^slurm-.*
				}
				reverse_proxy @slurmapps slurm:8889

				@goslurmapps {
					header_regexp goslurmapp Host ^goslurm-.*
				}
				reverse_proxy @goslurmapps go-slurm:8889

				@gok8sapps {
					header_regexp gok8sapp Host ^gok8s-.*
				}
				reverse_proxy @gok8sapps gok8s:8889
			}
		`, core2Config)),
	)

	cb.Service(
		"gateway",
		"Gateway",
		Json{
			// NOTE: The gateway is from this repo with no changes:
			// https://github.com/mholt/caddy-grpc-web
			// language=json
			`
				{
					"image": "dreg.cloud.sdu.dk/ucloud/caddy-gateway:1",
					"restart": "always",
					"volumes": [
						"` + gatewayData.GetAbsolutePath() + `:/data",
						"` + gatewayConfig.GetAbsolutePath() + `:/etc/caddy/Caddyfile",
						"` + certificates.GetAbsolutePath() + `:/certs"
					],
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(80)) + `:80",
						"` + strconv.Itoa(portAllocator.Allocate(443)) + `:443"
					],
					"hostname": "gateway"
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
