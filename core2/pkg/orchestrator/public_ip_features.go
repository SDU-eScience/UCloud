package orchestrator

const (
	publicIpFeatureFirewall SupportFeatureKey = "publicIp.firewall"
)

var publicIpFeatureMapper = []featureMapper{
	{
		Type: publicIpType,
		Key:  publicIpFeatureFirewall,
		Path: "firewall.enabled",
	},
}
