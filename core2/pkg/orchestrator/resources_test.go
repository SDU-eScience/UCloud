package orchestrator

import (
	"slices"
	"sync"
	"testing"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/assert"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var actorsMutex sync.Mutex
var actors = map[string]*rpc.Actor{}

func actor(username, project string) *rpc.Actor {
	actorsMutex.Lock()
	defer actorsMutex.Unlock()

	result := &rpc.Actor{
		Username:   username,
		Role:       rpc.RoleAdmin,
		Domain:     "example.com",
		OrgId:      "exampleOrg",
		Membership: map[rpc.ProjectId]rpc.ProjectRole{},
	}

	if project != "" {
		result.Project.Set(rpc.ProjectId(project))
		result.Membership[rpc.ProjectId(project)] = rpc.ProjectRoleAdmin
	}

	actors[username] = result
	return result
}

type TestResource struct {
	orcapi.Resource
	Status int
}

type TestResourceData struct {
	A int
	B int
}

const testResource = "test"

func initResourceTest(t *testing.T) {
	resourceGlobals.Testing.Enabled = true
	InitResources()
	InitResourceType(testResource, 0, nil, nil, func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags) any {
		d := extra.(*TestResourceData)
		return TestResource{
			Resource: r,
			Status:   d.A + d.B,
		}
	})

	rpc.LookupActor = func(username string) (rpc.Actor, bool) {
		actorsMutex.Lock()
		defer actorsMutex.Unlock()
		res, ok := actors[username]
		if ok {
			return *res, true
		} else {
			return rpc.Actor{}, false
		}
	}
}

func TestReadAndWritePath(t *testing.T) {
	initResourceTest(t)

	u := actor("user", "")

	p := ResourceBrowse(
		*u,
		testResource,
		util.OptNone[string](),
		500,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
	)

	assert.Equal(t, 0, len(p.Items))
	assert.False(t, p.Next.Present)

	id, doc, err := ResourceCreate[TestResource](
		*u,
		testResource,
		util.OptNone[accapi.ProductReference](),
		&TestResourceData{
			A: 1,
			B: 2,
		},
	)

	if assert.Nil(t, err) {
		ResourceConfirm(testResource, id)
	}
	assert.NotEqual(t, "", doc.Id)
	assert.Equal(t, u.Username, doc.Owner.CreatedBy)
	assert.Equal(t, "", doc.Owner.Project)
	assert.Equal(t, "", doc.ProviderGeneratedId)
	assert.Equal(t, 3, doc.Status)
	// Myself is undefined through this API, since it could be a registration. We do not expect to find
	// any permissions here.
	assert.Equal(t, 0, len(doc.Permissions.Myself))

	p = ResourceBrowse(
		*u,
		testResource,
		util.OptNone[string](),
		500,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
	)

	assert.Equal(t, 1, len(p.Items))
	assert.False(t, p.Next.Present)

	doc = p.Items[0]
	assert.Nil(t, err)
	assert.NotEqual(t, "", doc.Id)
	assert.Equal(t, u.Username, doc.Owner.CreatedBy)
	assert.Equal(t, "", doc.Owner.Project)
	assert.Equal(t, "", doc.ProviderGeneratedId)
	assert.Equal(t, 3, doc.Status)
	assert.Equal(t, 3, len(doc.Permissions.Myself))
	assert.True(t, slices.Contains(doc.Permissions.Myself, orcapi.PermissionRead))
	assert.True(t, slices.Contains(doc.Permissions.Myself, orcapi.PermissionEdit))
	assert.True(t, slices.Contains(doc.Permissions.Myself, orcapi.PermissionAdmin))

	ResourceDelete(*u, testResource, id)

	p = ResourceBrowse(
		*u,
		testResource,
		util.OptNone[string](),
		500,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
	)

	assert.Equal(t, 0, len(p.Items))
	assert.False(t, p.Next.Present)
}
