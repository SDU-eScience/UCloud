package foundation

import _ "embed"

//go:embed policies/restrict_api_tokens.yaml
var restrictAPITokens []byte

//go:embed policies/restrict_applications.yaml
var restrictApplications []byte

//go:embed policies/restrict_cut_and_paste.yaml
var restrictCutAndPast []byte

//go:embed policies/restrict_downloads.yaml
var restrictDownloads []byte

//go:embed policies/restrict_external_project_folder_mounting.yaml
var restrictExternalProjectFolderMounting []byte

//go:embed policies/restrict_integrated_applications.yaml
var restrictIntegratedApplications []byte

//go:embed policies/restrict_internet_access.yaml
var restrictInternetAccess []byte

//go:embed policies/restrict_move_and_copy.yaml
var restrictMoveAndCopy []byte

//go:embed policies/restrict_organizations_members.yaml
var restrictOrganizationsMembers []byte

//go:embed policies/restrict_provider_file_transfers.yaml
var restrictProviderTransfers []byte

//go:embed policies/restrict_public_ips.yaml
var restrictPublicIPs []byte

//go:embed policies/restrict_public_links.yaml
var restrictPublicLinks []byte

//go:embed policies/restrict_shares.yaml
var restrictShares []byte

//go:embed policies/restrict_source_ip_range.yaml
var restrictSourceIpRange []byte

//go:embed policies/restrict_ssh.yaml
var restrictSSH []byte

//go:embed policies/restrict_uploads.yaml
var restrictUploads []byte

type LoadedPolicy struct {
	PolicyName string
	Bytes      []byte
}

func pullProjectPolicies() []LoadedPolicy {
	policies := []LoadedPolicy{
		{
			PolicyName: "restrictApiTokens",
			Bytes:      restrictAPITokens,
		},
		{
			PolicyName: "restrictApplications",
			Bytes:      restrictApplications,
		},
		{
			PolicyName: "restrictCutAndPast",
			Bytes:      restrictCutAndPast,
		},
		{
			PolicyName: "restrictDownloads",
			Bytes:      restrictDownloads,
		},
		{
			PolicyName: "RestrictExternalProjectFolderMounting",
			Bytes:      restrictExternalProjectFolderMounting,
		},
		{
			PolicyName: "restrictIntegratedApplications",
			Bytes:      restrictIntegratedApplications,
		},
		{
			PolicyName: "restrictInternetAccess",
			Bytes:      restrictInternetAccess,
		},
		{
			PolicyName: "restrictMoveAndCopy",
			Bytes:      restrictMoveAndCopy,
		},
		{
			PolicyName: "restrictOrganizationsMembers",
			Bytes:      restrictOrganizationsMembers,
		},
		{
			PolicyName: "restrictProviderTransfers",
			Bytes:      restrictProviderTransfers,
		},
		{
			PolicyName: "restrictPublicIPs",
			Bytes:      restrictPublicIPs,
		},
		{
			PolicyName: "restrictPublicLinks",
			Bytes:      restrictPublicLinks,
		},
		{
			PolicyName: "restrictSourceIpRange",
			Bytes:      restrictSourceIpRange,
		},
	}
	return policies
}
