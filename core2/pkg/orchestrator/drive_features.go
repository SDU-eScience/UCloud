package orchestrator

const (
	driveStatsSize          featureKey = "drive.stats.size"
	driveStatsRecursiveSize featureKey = "drive.stats.recursiveSize"
	driveStatsTimestamps    featureKey = "drive.stats.timestamps"
	driveStatsUnix          featureKey = "drive.stats.unix"

	// NOTE(Dan): No acl for files
	driveOpsTrash           featureKey = "drive.ops.trash"
	driveOpsReadOnly        featureKey = "drive.ops.readOnly"
	driveOpsSearch          featureKey = "drive.ops.search"
	driveOpsStreamingSearch featureKey = "drive.ops.streamingSearch"
	driveOpsShares          featureKey = "drive.ops.shares"
	driveOpsTerminal        featureKey = "drive.ops.terminal"

	driveAcl        featureKey = "drive.acl"
	driveManagement featureKey = "drive.management" // create & rename
	driveDeletion   featureKey = "drive.deletion"
)

var driveFeatureMapper = []featureMapper{
	{
		Type: driveType,
		Key:  driveAcl,
		Path: "collection.aclModifiable",
	},
	{
		Type: driveType,
		Key:  driveManagement,
		Path: "collection.usersCanCreate",
	},
	{
		Type: driveType,
		Key:  driveDeletion,
		Path: "collection.usersCanDelete",
	},
	{
		Type: driveType,
		Key:  driveManagement,
		Path: "collection.usersCanRename",
	},

	{
		Type: driveType,
		Key:  "", // no longer supported but keep in legacy (always false)
		Path: "files.aclModifiable",
	},
	{
		Type: driveType,
		Key:  driveOpsTrash,
		Path: "files.trashSupported",
	},
	{
		Type: driveType,
		Key:  driveOpsReadOnly,
		Path: "files.isReadOnly",
	},
	{
		Type: driveType,
		Key:  driveOpsSearch,
		Path: "files.searchSupported",
	},
	{
		Type: driveType,
		Key:  driveOpsStreamingSearch,
		Path: "files.streamingSearchSupported",
	},
	{
		Type: driveType,
		Key:  driveOpsShares,
		Path: "files.sharesSupported",
	},
	{
		Type: driveType,
		Key:  driveOpsTerminal,
		Path: "files.openInTerminal",
	},

	{
		Type: driveType,
		Key:  driveStatsSize,
		Path: "stats.sizeInBytes",
	},
	{
		Type: driveType,
		Key:  driveStatsRecursiveSize,
		Path: "stats.sizeIncludingChildrenInBytes",
	},
	{
		Type: driveType,
		Key:  driveStatsTimestamps,
		Path: "stats.modifiedAt",
	},
	{
		Type: driveType,
		Key:  driveStatsTimestamps,
		Path: "stats.createdAt",
	},
	{
		Type: driveType,
		Key:  driveStatsTimestamps,
		Path: "stats.accessedAt",
	},
	{
		Type: driveType,
		Key:  driveStatsUnix,
		Path: "stats.unixPermissions",
	},
	{
		Type: driveType,
		Key:  driveStatsUnix,
		Path: "stats.unixOwner",
	},
	{
		Type: driveType,
		Key:  driveStatsUnix,
		Path: "stats.unixGroup",
	},
}
