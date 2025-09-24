package orchestrator

const (
	ingressFeaturePrefix SupportFeatureKey = "ingress.prefix"
	ingressFeatureSuffix SupportFeatureKey = "ingress.suffix"
)

var ingressFeatureMapper = []featureMapper{
	{
		Type: ingressType,
		Key:  ingressFeaturePrefix,
		Path: "domainPrefix",
	},
	{
		Type: ingressType,
		Key:  ingressFeatureSuffix,
		Path: "domainSuffix",
	},
}
