package orchestrator

import (
	"slices"
	"strings"
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
	InitResourceType(testResource, 0, nil, nil, func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags, actor rpc.Actor) any {
		d := extra.(*TestResourceData)
		return TestResource{
			Resource: r,
			Status:   d.A + d.B,
		}
	}, nil)

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
		nil,
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
		nil,
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
		nil,
	)

	assert.Equal(t, 0, len(p.Items))
	assert.False(t, p.Next.Present)
}

func TestPagination(t *testing.T) {
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
		nil,
	)

	assert.Equal(t, 0, len(p.Items))
	assert.False(t, p.Next.Present)

	ids := map[ResourceId]util.Empty{}
	for i := 0; i < 10; i++ {
		id, _, err := ResourceCreate[TestResource](
			*u,
			testResource,
			util.OptNone[accapi.ProductReference](),
			&TestResourceData{
				A: 1,
				B: 2,
			},
		)

		assert.Nil(t, err)
		ResourceConfirm(testResource, id)

		ids[id] = util.Empty{}
	}

	for itemsPerPage := 1; itemsPerPage <= 30; itemsPerPage++ {
		idsSeen := map[ResourceId]util.Empty{}
		idsSeenCount := 0

		var next util.Option[string]
		for i := 0; i < 50; i++ {
			p = ResourceBrowse(
				*u,
				testResource,
				next,
				itemsPerPage,
				orcapi.ResourceFlags{},
				func(item TestResource) bool {
					return true
				},
				nil,
			)

			for _, item := range p.Items {
				idsSeen[ResourceParseId(item.Id)] = util.Empty{}
				idsSeenCount++
			}

			next = p.Next
			if !next.Present {
				break
			}
		}

		assert.Equal(t, len(ids), len(idsSeen))
		for id := range ids {
			_, found := idsSeen[id]
			assert.True(t, found, "expected to find %v", id)
		}

		assert.Equal(t, len(ids), idsSeenCount)
	}

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("ffdii"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		nil,
	)

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("-100"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		nil,
	)

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("133333392931034134235538821288300"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		nil,
	)
}

func TestPaginationWithSorter(t *testing.T) {
	initResourceTest(t)

	u := actor("user", "")

	comparator := func(a TestResource, b TestResource) int {
		return strings.Compare(a.Id, b.Id)
	}

	p := ResourceBrowse(
		*u,
		testResource,
		util.OptNone[string](),
		500,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		comparator,
	)

	assert.Equal(t, 0, len(p.Items))
	assert.False(t, p.Next.Present)

	ids := map[ResourceId]util.Empty{}
	for i := 0; i < 10; i++ {
		id, _, err := ResourceCreate[TestResource](
			*u,
			testResource,
			util.OptNone[accapi.ProductReference](),
			&TestResourceData{
				A: 1,
				B: 2,
			},
		)

		assert.Nil(t, err)
		ResourceConfirm(testResource, id)

		ids[id] = util.Empty{}
	}

	for itemsPerPage := 1; itemsPerPage <= 30; itemsPerPage++ {
		idsSeen := map[ResourceId]util.Empty{}
		idsSeenCount := 0

		var next util.Option[string]
		for i := 0; i < 50; i++ {
			p = ResourceBrowse(
				*u,
				testResource,
				next,
				itemsPerPage,
				orcapi.ResourceFlags{},
				func(item TestResource) bool {
					return true
				},
				comparator,
			)

			for _, item := range p.Items {
				idsSeen[ResourceParseId(item.Id)] = util.Empty{}
				idsSeenCount++
			}

			next = p.Next
			if !next.Present {
				break
			}
		}

		assert.Equal(t, len(ids), len(idsSeen))
		for id := range ids {
			_, found := idsSeen[id]
			assert.True(t, found, "expected to find %v", id)
		}

		assert.Equal(t, len(ids), idsSeenCount)
	}

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("ffdii"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		comparator,
	)

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("-100"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		comparator,
	)

	// Check that pagination with bad identifier works
	p = ResourceBrowse(
		*u,
		testResource,
		util.OptValue("133333392931034134235538821288300"),
		100,
		orcapi.ResourceFlags{},
		func(item TestResource) bool {
			return true
		},
		comparator,
	)
}
