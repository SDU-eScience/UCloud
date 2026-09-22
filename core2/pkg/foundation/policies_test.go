package foundation

import (
	"slices"
	"testing"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initPoliciesTest(t *testing.T) {
	t.Helper()

	policyGlobals.TestingEnabled = true
	policyGlobals.TestDefaultPolicySettings = map[string]map[fndapi.PolicyName]fndapi.Specification{}

	supportiveRoleGlobals.TestingEnabled = true
	supportiveRoleGlobals.TestHolders = map[string]map[fndapi.SupportiveRole]string{}

	// The schema cache is normally populated by initPolicies(). It does not require a database and is needed by
	// policiesUpdate to validate the updated policies.
	policyPopulateSchemaCache()

	projectPolicies.Mu.Lock()
	projectPolicies.PoliciesByProject = map[string]*AssociatedPolicies{}
	projectPolicies.Mu.Unlock()
}

func policyTestActor(username string, project string) rpc.Actor {
	SupportiveRoleSetHolderForTesting(project, fndapi.SupportiveRoleDataManager, username)

	return rpc.Actor{
		Username: username,
		Role:     rpc.RoleUser,
		Project:  util.OptValue(rpc.ProjectId(project)),
		Membership: map[rpc.ProjectId]rpc.ProjectRole{
			rpc.ProjectId(project): rpc.ProjectRoleUser,
		},
	}
}

func sshSpecification(project string, enabled bool) fndapi.Specification {
	return &fndapi.RestrictSshSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictSshValues]{
			Schema:  fndapi.RestrictSsh,
			Project: rpc.ProjectId(project),
			Values:  fndapi.RestrictSshValues{Enabled: enabled},
		},
	}
}

func uploadSpecification(project string, enabled bool) fndapi.Specification {
	return &fndapi.RestrictUploadsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictUploadsValues]{
			Schema:  fndapi.RestrictUploads,
			Project: rpc.ProjectId(project),
			Values:  fndapi.RestrictUploadsValues{Enabled: enabled},
		},
	}
}

func applicationsSpecification(project string, enabled bool, applications []string) fndapi.Specification {
	return &fndapi.RestrictApplicationsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictApplicationsValues]{
			Schema:  fndapi.RestrictApplications,
			Project: rpc.ProjectId(project),
			Values: fndapi.RestrictApplicationsValues{
				Enabled:      enabled,
				Applications: applications,
			},
		},
	}
}

func internetAccessSpecification(project string, enabled bool, allowedSubnets string) fndapi.Specification {
	return &fndapi.RestrictInternetAccessSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictInternetAccessValues]{
			Schema:  fndapi.RestrictInternetAccess,
			Project: rpc.ProjectId(project),
			Values: fndapi.RestrictInternetAccessValues{
				Enabled:        enabled,
				AllowedSubnets: allowedSubnets,
			},
		},
	}
}

func mustUpdatePolicies(t *testing.T, actor rpc.Actor, defaultPolicy bool, specifications ...fndapi.Specification) {
	t.Helper()

	updated := map[fndapi.PolicyName]fndapi.Specification{}
	for _, specification := range specifications {
		updated[specification.GetSpecificationName()] = specification
	}

	_, err := policiesUpdate(actor, fndapi.PoliciesUpdateRequest{
		UpdatedPolicies: updated,
		DefaultPolicy:   defaultPolicy,
	})
	if err != nil {
		t.Fatalf("policiesUpdate error: %+v", err)
	}
}

func mustRetrievePolicies(t *testing.T, project string) map[fndapi.PolicyName]fndapi.Policy {
	t.Helper()

	supportiveRoleGlobals.Mu.RLock()
	holder, hasHolder := supportiveRoleGlobals.TestHolders[project][fndapi.SupportiveRoleDataManager]
	supportiveRoleGlobals.Mu.RUnlock()
	if !hasHolder {
		holder = "policy-checker"
	}

	actor := policyTestActor(holder, project)
	result, err := policiesRetrieve(actor, fndapi.RetrievePoliciesRequest{})
	if err != nil {
		t.Fatalf("policiesRetrieve error: %+v", err)
	}
	return result
}

func assertPolicyEnabled(t *testing.T, policies map[fndapi.PolicyName]fndapi.Policy, name fndapi.PolicyName, enabled bool) {
	t.Helper()

	policy, ok := policies[name]
	if !ok {
		t.Fatalf("expected a schema for the %v policy", name)
	}

	if policy.Specification == nil {
		if enabled {
			t.Fatalf("expected the %v policy to be enabled, but it is not configured", name)
		}
		return
	}

	if policy.Specification.IsEnabled() != enabled {
		t.Fatalf("expected the %v policy to be enabled=%v", name, enabled)
	}
}

func assertApplicationsPolicy(t *testing.T, policies map[fndapi.PolicyName]fndapi.Policy, applications []string) {
	t.Helper()

	specification, ok := policies[fndapi.RestrictApplications].Specification.(*fndapi.RestrictApplicationsSpecification)
	if !ok {
		t.Fatalf("expected an applications policy specification, got %T", policies[fndapi.RestrictApplications].Specification)
	}

	if !slices.Equal(specification.Values.Applications, applications) {
		t.Fatalf("expected allowed applications %v but got %v", applications, specification.Values.Applications)
	}
}

func assertInternetAccessPolicy(t *testing.T, policies map[fndapi.PolicyName]fndapi.Policy, allowedSubnets string) {
	t.Helper()

	specification, ok := policies[fndapi.RestrictInternetAccess].Specification.(*fndapi.RestrictInternetAccessSpecification)
	if !ok {
		t.Fatalf("expected an internet access policy specification, got %T", policies[fndapi.RestrictInternetAccess].Specification)
	}

	if specification.Values.AllowedSubnets != allowedSubnets {
		t.Fatalf("expected allowed subnets %q but got %q", allowedSubnets, specification.Values.AllowedSubnets)
	}
}

func TestProjectPolicyEnableDisable(t *testing.T) {
	initPoliciesTest(t)

	const project = "policy-project"
	admin := policyTestActor("policy-admin", project)

	// No policy has been configured yet, all policies are disabled by default
	policies := mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, false)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// Enable a policy and check that it is set
	mustUpdatePolicies(t, admin, false, sshSpecification(project, true))
	policies = mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// Enable a second policy, the first one must remain untouched
	mustUpdatePolicies(t, admin, false, uploadSpecification(project, true))
	policies = mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, true)

	// Change (disable) the first policy, the second one must remain untouched
	mustUpdatePolicies(t, admin, false, sshSpecification(project, false))
	policies = mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, false)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, true)

	// Re-enable it to verify that changes work in both directions
	mustUpdatePolicies(t, admin, false, sshSpecification(project, true))
	policies = mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
}

func TestDefaultPoliciesAppliedToNewSubproject(t *testing.T) {
	initPoliciesTest(t)

	const giver = "default-giver"
	giverAdmin := policyTestActor("giver-admin", giver)

	// The grant giver has not saved any defaults. The hardcoded default (all policies disabled) applies and no
	// work is needed for a new subproject.
	const subproject = "default-sub-1"
	applyDefaultPoliciesToNewSubproject([]string{giver}, subproject)
	policies := mustRetrievePolicies(t, subproject)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, false)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// Save a default policy setting on the grant giver and create a new subproject through a grant
	mustUpdatePolicies(t, giverAdmin, true,
		sshSpecification(giver, true),
		uploadSpecification(giver, false),
	)

	// The saved setting can be retrieved again
	defaults, err := policiesDefaultRetrieve(giverAdmin, fndapi.RetrievePoliciesRequest{})
	if err != nil {
		t.Fatalf("policiesDefaultRetrieve error: %+v", err)
	}
	assertPolicyEnabled(t, defaults, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, defaults, fndapi.RestrictUploads, false)

	// The new subproject receives the enabled policy of the setting. Disabled policies are equivalent to
	// unconfigured policies and are not applied.
	const subproject2 = "default-sub-2"
	applyDefaultPoliciesToNewSubproject([]string{giver}, subproject2)
	policies = mustRetrievePolicies(t, subproject2)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// The merged defaults also become the default policy setting of the subproject itself
	subAdmin := policyTestActor("sub-admin", subproject2)
	defaults, err = policiesDefaultRetrieve(subAdmin, fndapi.RetrievePoliciesRequest{})
	if err != nil {
		t.Fatalf("policiesDefaultRetrieve error: %+v", err)
	}
	assertPolicyEnabled(t, defaults, fndapi.RestrictSsh, true)

	// ...and therefore propagate to subprojects created by the subproject
	const grandchild = "default-sub-2-child"
	applyDefaultPoliciesToNewSubproject([]string{subproject2}, grandchild)
	policies = mustRetrievePolicies(t, grandchild)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
}

func TestMultiGrantGiverDefaultMerging(t *testing.T) {
	initPoliciesTest(t)

	const giver1 = "merge-giver-1"
	const giver2 = "merge-giver-2"

	admin1 := policyTestActor("merge-admin-1", giver1)
	admin2 := policyTestActor("merge-admin-2", giver2)

	// Giver 1 restricts SSH, applications and internet access
	mustUpdatePolicies(t, admin1, true,
		sshSpecification(giver1, true),
		applicationsSpecification(giver1, true, []string{"app-a", "app-b"}),
		internetAccessSpecification(giver1, true, "10.0.0.0/8"),
	)

	// Giver 2 does not restrict SSH but restricts applications and internet access harder than giver 1
	mustUpdatePolicies(t, admin2, true,
		applicationsSpecification(giver2, true, []string{"app-b", "app-c"}),
		internetAccessSpecification(giver2, true, "10.1.0.0/16"),
		uploadSpecification(giver2, true),
	)

	const subproject = "merge-sub-1"
	applyDefaultPoliciesToNewSubproject([]string{giver1, giver2}, subproject)
	policies := mustRetrievePolicies(t, subproject)

	// Flag only policies are enabled if enabled by at least one grant giver (union of restrictions)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, true)

	// Allow-lists are intersected across all grant givers which have the policy enabled
	assertPolicyEnabled(t, policies, fndapi.RestrictApplications, true)
	assertApplicationsPolicy(t, policies, []string{"app-b"})

	// Subnets are intersected, the smaller block wins when one block contains the other
	assertPolicyEnabled(t, policies, fndapi.RestrictInternetAccess, true)
	assertInternetAccessPolicy(t, policies, "10.1.0.0/16")

	// Disjoint blocks have an empty intersection, that is, no address is allowed by all grant givers
	mustUpdatePolicies(t, admin2, true, internetAccessSpecification(giver2, true, "192.168.0.0/16"))

	const subproject2 = "merge-sub-2"
	applyDefaultPoliciesToNewSubproject([]string{giver1, giver2}, subproject2)
	policies = mustRetrievePolicies(t, subproject2)
	assertPolicyEnabled(t, policies, fndapi.RestrictInternetAccess, true)
	assertInternetAccessPolicy(t, policies, "")
}

func TestGrantGiverDefaultChangeDoesNotUpdateExistingSubproject(t *testing.T) {
	initPoliciesTest(t)

	const giver = "snapshot-giver"
	giverAdmin := policyTestActor("snapshot-admin", giver)

	// A subproject is created through a grant while the giver's defaults restrict SSH
	mustUpdatePolicies(t, giverAdmin, true, sshSpecification(giver, true))

	const subproject = "snapshot-sub-1"
	applyDefaultPoliciesToNewSubproject([]string{giver}, subproject)
	policies := mustRetrievePolicies(t, subproject)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// The grant giver changes the defaults and grants new resources to the already existing subproject. Grants
	// to existing projects never pass through the default policy application (it only runs when the subproject
	// is created, guarded by wasNewlyCreated in ProjectCreateInternal), so the policies of the existing
	// subproject must stay as they were at creation time.
	mustUpdatePolicies(t, giverAdmin, true,
		sshSpecification(giver, false),
		uploadSpecification(giver, true),
	)

	// Check defaults of the grant changed
	defaults, err := policiesDefaultRetrieve(giverAdmin, fndapi.RetrievePoliciesRequest{})
	if err != nil {
		t.Fatalf("policiesDefaultRetrieve error: %+v", err)
	}
	assertPolicyEnabled(t, defaults, fndapi.RestrictSsh, false)
	assertPolicyEnabled(t, defaults, fndapi.RestrictUploads, true)

	// Existing subproject is does not change
	policies = mustRetrievePolicies(t, subproject)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, false)

	// A new subproject created by a later grant does receive the new defaults
	const subproject2 = "snapshot-sub-2"
	applyDefaultPoliciesToNewSubproject([]string{giver}, subproject2)
	policies = mustRetrievePolicies(t, subproject2)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, false)
	assertPolicyEnabled(t, policies, fndapi.RestrictUploads, true)
}

func TestPoliciesRequireDataManager(t *testing.T) {
	initPoliciesTest(t)

	const project = "dm-required-project"

	// The data manager is a regular project member (role User) which holds the supportive role
	dm := policyTestActor("data-manager", project)

	// A project admin without the data manager supportive role may not read or update the policies
	admin := rpc.Actor{
		Username: "plain-admin",
		Role:     rpc.RoleUser,
		Project:  util.OptValue(rpc.ProjectId(project)),
		Membership: map[rpc.ProjectId]rpc.ProjectRole{
			rpc.ProjectId(project): rpc.ProjectRoleAdmin,
		},
	}

	_, err := policiesRetrieve(admin, fndapi.RetrievePoliciesRequest{})
	if err == nil {
		t.Fatalf("expected policiesRetrieve to fail for a member without the data manager role")
	}

	_, err = policiesDefaultRetrieve(admin, fndapi.RetrievePoliciesRequest{})
	if err == nil {
		t.Fatalf("expected policiesDefaultRetrieve to fail for a member without the data manager role")
	}

	_, err = policiesUpdate(admin, fndapi.PoliciesUpdateRequest{
		UpdatedPolicies: map[fndapi.PolicyName]fndapi.Specification{
			fndapi.RestrictSsh: sshSpecification(project, true),
		},
	})
	if err == nil {
		t.Fatalf("expected policiesUpdate to fail for a member without the data manager role")
	}

	// The data manager may update and read the policies of the project
	mustUpdatePolicies(t, dm, false, sshSpecification(project, true))
	policies := mustRetrievePolicies(t, project)
	assertPolicyEnabled(t, policies, fndapi.RestrictSsh, true)
}
