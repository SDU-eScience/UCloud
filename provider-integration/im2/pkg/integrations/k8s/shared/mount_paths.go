package shared

import orc "ucloud.dk/shared/pkg/orchestrators"

func ValidateFileMountPath(path string) (string, bool) {
	return orc.ValidateFileMountPath(path)
}

func ValidateExplicitFileMountPaths(values []orc.AppParameterValue) (bool, string) {
	return orc.ValidateExplicitFileMountPaths(values)
}
