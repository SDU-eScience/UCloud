package controller

import (
	"fmt"

	"ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type JobResourceAccessibilityIssue string

const (
	JobResourceAccessibilityIssueNone        JobResourceAccessibilityIssue = ""
	JobResourceAccessibilityIssueUnavailable JobResourceAccessibilityIssue = "UNAVAILABLE"
	JobResourceAccessibilityIssuePermission  JobResourceAccessibilityIssue = "PERMISSION_DENIED"
	JobResourceAccessibilityIssueLocked      JobResourceAccessibilityIssue = "LOCKED"
)

func ResourceCanUse(actor orc.ResourceOwner, owner orc.ResourceOwner, permissions util.Option[orc.ResourcePermissions], readOnly bool) bool {
	requiredPermission := orc.PermissionEdit
	if readOnly {
		requiredPermission = orc.PermissionRead
	}

	if owner.Project.Present {
		project, ok := ProjectRetrieve(owner.Project.Value)
		if !ok {
			return false
		}

		username := actor.CreatedBy
		if username != "" {
			isMember := false
			for _, member := range project.Status.Members {
				if member.Username == username {
					isMember = true
					if member.Role == foundation.ProjectRolePI || member.Role == foundation.ProjectRoleAdmin {
						return true
					}
				}
			}

			if !isMember {
				return false
			}

			if username == owner.CreatedBy {
				return true
			}

			for _, entry := range permissions.GetOrDefault(orc.ResourcePermissions{}).Others {
				if !orc.PermissionsHas(entry.Permissions, requiredPermission) {
					continue
				}

				switch entry.Entity.Type {
				case orc.AclEntityTypeUser:
					if entry.Entity.Username == username {
						return true
					}

				case orc.AclEntityTypeProjectGroup:
					if foundation.IsMemberOfGroup(project, entry.Entity.Group, username) {
						return true
					}
				}
			}

			return false
		}

		return project.Id == actor.Project.Value
	}

	for _, entry := range permissions.GetOrDefault(orc.ResourcePermissions{}).Others {
		if !orc.PermissionsHas(entry.Permissions, requiredPermission) {
			continue
		}

		if entry.Entity.Type == orc.AclEntityTypeUser && entry.Entity.Username == actor.CreatedBy {
			return true
		}
	}

	return actor.CreatedBy == owner.CreatedBy
}

func JobResourceIsAccessible(owner orc.ResourceOwner, resource orc.AppParameterValue) (bool, string, JobResourceAccessibilityIssue) {
	// TODO(Dan): This has become a bit of a copy & pasted mess. Refactor this later.

	hasEmptyPermissions := func(permissions util.Option[orc.ResourcePermissions]) bool {
		if !permissions.Present {
			return true
		}

		return len(permissions.Value.Myself) == 0 && len(permissions.Value.Others) == 0
	}

	// NOTE(Dan): Refresh functions are needed since the database has old entries which doesn't include actual ACLs,
	// which is causing the resources to fail permission check that this shouldn't fail.

	refreshPublicIp := func(id string) (*orc.PublicIp, bool) {
		publicIps.Mu.Lock()
		delete(publicIps.Ips, id)
		publicIps.Mu.Unlock()

		request := orc.PublicIpsControlRetrieveRequest{Id: id}
		request.IncludeOthers = true
		request.IncludeProduct = false
		request.IncludeUpdates = true

		ip, err := orc.PublicIpsControlRetrieve.Invoke(request)
		if err != nil {
			return nil, false
		}

		PublicIpTrackNew(ip)
		return &ip, true
	}

	refreshIngress := func(id string) (orc.Ingress, bool) {
		ingressesMutex.Lock()
		delete(ingresses, id)
		ingressesMutex.Unlock()

		request := orc.IngressesControlRetrieveRequest{Id: id}
		request.IncludeOthers = true

		ingress, err := orc.IngressesControlRetrieve.Invoke(request)
		if err != nil {
			return orc.Ingress{}, false
		}

		LinkTrack(ingress)
		return ingress, true
	}

	refreshLicense := func(id string) (*orc.License, bool) {
		licenseMutex.Lock()
		delete(licenses, id)
		licenseMutex.Unlock()

		request := orc.LicensesControlRetrieveRequest{Id: id}
		request.IncludeOthers = true

		license, err := orc.LicensesControlRetrieve.Invoke(request)
		if err != nil {
			return nil, false
		}

		LicenseTrack(license)
		return &license, true
	}

	refreshPrivateNetwork := func(id string) (orc.PrivateNetwork, bool) {
		privateNetworkMutex.Lock()
		delete(privateNetworks, id)
		privateNetworkMutex.Unlock()

		request := orc.PrivateNetworksControlRetrieveRequest{Id: id}
		request.IncludeOthers = true

		network, err := orc.PrivateNetworksControlRetrieve.Invoke(request)
		if err != nil {
			return orc.PrivateNetwork{}, false
		}

		PrivateNetworkTrackNew(network)
		return network, true
	}

	switch resource.Type {
	case orc.AppParameterValueTypeNetwork:
		ip, ok := PublicIpRetrieve(resource.Id)
		if !ok {
			return false, "Public IP is no longer valid. Was it deleted?", JobResourceAccessibilityIssueUnavailable
		}

		if !ResourceCanUse(owner, ip.Owner, ip.Permissions, false) {
			if hasEmptyPermissions(ip.Permissions) {
				refreshed, refreshOk := refreshPublicIp(resource.Id)
				if refreshOk {
					ip = refreshed
				}
			}

			if ResourceCanUse(owner, ip.Owner, ip.Permissions, false) {
				if ResourceIsLocked(ip.Resource, ip.Specification.Product) {
					return false, fmt.Sprintf("Insufficient funds for %v", ip.Specification.Product.Category), JobResourceAccessibilityIssueLocked
				}
				return true, "", JobResourceAccessibilityIssueNone
			}

			return false, fmt.Sprintf("You no longer have permissions to use this public IP: %s.", ip.Id), JobResourceAccessibilityIssuePermission
		}

		if ResourceIsLocked(ip.Resource, ip.Specification.Product) {
			return false, fmt.Sprintf("Insufficient funds for %v", ip.Specification.Product.Category), JobResourceAccessibilityIssueLocked
		}

		return true, "", JobResourceAccessibilityIssueNone

	case orc.AppParameterValueTypeIngress:
		ingress := LinkRetrieve(resource.Id)
		if ingress.Id == "" {
			return false, "Public link is no longer valid. Was it deleted?", JobResourceAccessibilityIssueUnavailable
		}

		if !ResourceCanUse(owner, ingress.Owner, ingress.Permissions, false) {
			if hasEmptyPermissions(ingress.Permissions) {
				refreshed, refreshOk := refreshIngress(resource.Id)
				if refreshOk {
					ingress = refreshed
				}
			}

			if ResourceCanUse(owner, ingress.Owner, ingress.Permissions, false) {
				return true, "", JobResourceAccessibilityIssueNone
			}

			return false, fmt.Sprintf("You no longer have permissions to use this public link: %s.", ingress.Specification.Domain), JobResourceAccessibilityIssuePermission
		}

		return true, "", JobResourceAccessibilityIssueNone

	case orc.AppParameterValueTypeLicense:
		license, ok := LicenseRetrieveInstance(resource.Id)
		if !ok {
			return false, "License is no longer valid. Was it deleted?", JobResourceAccessibilityIssueUnavailable
		}

		if !ResourceCanUse(owner, license.Owner, license.Permissions, false) {
			if hasEmptyPermissions(license.Permissions) {
				refreshed, refreshOk := refreshLicense(resource.Id)
				if refreshOk {
					license = refreshed
				}
			}

			if ResourceCanUse(owner, license.Owner, license.Permissions, false) {
				if ResourceIsLocked(license.Resource, license.Specification.Product) {
					return false, fmt.Sprintf("Insufficient funds for %v", license.Specification.Product.Category), JobResourceAccessibilityIssueLocked
				}
				return true, "", JobResourceAccessibilityIssueNone
			}

			return false, fmt.Sprintf("You no longer have permissions to use this license: %s.", license.Id), JobResourceAccessibilityIssuePermission
		}

		if ResourceIsLocked(license.Resource, license.Specification.Product) {
			return false, fmt.Sprintf("Insufficient funds for %v", license.Specification.Product.Category), JobResourceAccessibilityIssueLocked
		}

		return true, "", JobResourceAccessibilityIssueNone

	case orc.AppParameterValueTypePrivateNetwork:
		network, ok := PrivateNetworkRetrieve(resource.Id)
		if !ok {
			return false, "Private network is no longer valid. Was it deleted?", JobResourceAccessibilityIssueUnavailable
		}

		if !ResourceCanUse(owner, network.Owner, network.Permissions, false) {
			if hasEmptyPermissions(network.Permissions) {
				refreshed, refreshOk := refreshPrivateNetwork(resource.Id)
				if refreshOk {
					network = refreshed
				}
			}

			if ResourceCanUse(owner, network.Owner, network.Permissions, false) {
				return true, "", JobResourceAccessibilityIssueNone
			}

			return false, fmt.Sprintf("You no longer have permissions to use this private network: %s.", network.Id), JobResourceAccessibilityIssuePermission
		}

		return true, "", JobResourceAccessibilityIssueNone

	default:
		return true, "", JobResourceAccessibilityIssueNone
	}
}
