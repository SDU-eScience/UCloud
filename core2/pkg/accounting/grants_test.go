package accounting

import (
	"net/http"
	"strconv"
	"sync"
	"testing"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/assert"
	fndapi "ucloud.dk/shared/pkg/foundation"
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
		result.Project.Set(project)
		result.Membership[rpc.ProjectId(project)] = rpc.ProjectRoleAdmin
	}

	actors[username] = result
	return result
}

func addGrantGiver(t *testing.T, id string) {
	b := grantGetSettingsBucket(id)

	b.Mu.Lock()
	defer b.Mu.Unlock()

	b.PublicGrantGivers[id] = util.Empty{}
	b.Settings[id] = &grantSettings{
		ProjectId: id,
		Settings: &accapi.GrantRequestSettings{
			Enabled:     true,
			Description: "test giver",
			AllowRequestsFrom: []accapi.UserCriteria{
				{Type: accapi.UserCriteriaTypeAnyone},
			},
			Templates: accapi.Templates{Type: accapi.TemplatesTypePlainText},
		},
	}

	owner := internalOwnerByReference(id).Id
	{
		accBucket := internalBucketOrInit(cpuCategory)
		w := internalWalletByOwner(accBucket, time.Now(), owner)
		_, _ = internalAllocate(time.Now(), accBucket, time.Now(), time.Now().AddDate(1, 0, 0), 1000000, w, 0,
			util.OptNone[accGrantId]())
	}
	{
		accBucket := internalBucketOrInit(storageCategory)
		w := internalWalletByOwner(accBucket, time.Now(), owner)
		_, _ = internalAllocate(time.Now(), accBucket, time.Now(), time.Now().AddDate(1, 0, 0), 1000000, w, 0,
			util.OptNone[accGrantId]())
	}
}

func rev(user, giver string, quota int64) accapi.GrantsSubmitRevisionRequest {
	return accapi.GrantsSubmitRevisionRequest{
		Comment: "c",
		Revision: accapi.GrantDocument{
			Recipient: accapi.Recipient{
				Type:     accapi.RecipientTypePersonalWorkspace,
				Username: util.OptValue(user),
			},
			AllocationRequests: []accapi.AllocationRequest{{
				Category:         cpuCategory.Name,
				Provider:         cpuCategory.Provider,
				GrantGiver:       giver,
				BalanceRequested: util.OptValue(quota),
				Period: accapi.Period{
					End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
				},
			}},
			AllocationPeriod: util.OptValue(accapi.Period{
				End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
			}),
			Form: accapi.Form{Type: accapi.FormTypePlainText, Text: "x"},
		},
	}
}

var cpuProduct = accapi.ProductV2{
	Type: accapi.ProductTypeCCompute,
	Category: accapi.ProductCategory{
		Name:        "CPU",
		Provider:    "provider",
		ProductType: accapi.ProductTypeCompute,
		AccountingUnit: accapi.AccountingUnit{
			Name:                   "Core",
			NamePlural:             "Core",
			FloatingPoint:          false,
			DisplayFrequencySuffix: true,
		},
		AccountingFrequency: accapi.AccountingFrequencyPeriodicHour,
		FreeToUse:           false,
		AllowSubAllocations: true,
	},
	Name:         "CPU",
	Description:  "CPU",
	ProductType:  accapi.ProductTypeCompute,
	Price:        1,
	Cpu:          1,
	MemoryInGigs: 1,
}

var cpuCategory = cpuProduct.Category

var storageProduct = accapi.ProductV2{
	Type: accapi.ProductTypeCStorage,
	Category: accapi.ProductCategory{
		Name:        "Storage",
		Provider:    "provider",
		ProductType: accapi.ProductTypeCompute,
		AccountingUnit: accapi.AccountingUnit{
			Name:                   "GB",
			NamePlural:             "GB",
			FloatingPoint:          false,
			DisplayFrequencySuffix: true,
		},
		AccountingFrequency: accapi.AccountingFrequencyOnce,
		FreeToUse:           false,
		AllowSubAllocations: true,
	},
	Name:        "Storage",
	Description: "Storage",
	ProductType: accapi.ProductTypeStorage,
}

var storageCategory = storageProduct.Category

func initGrantsTest(t *testing.T) {
	accGlobals.OwnersByReference = make(map[string]*internalOwner)
	accGlobals.OwnersById = make(map[accOwnerId]*internalOwner)
	accGlobals.Usage = make(map[string]*scopedUsage)
	accGlobals.BucketsByCategory = make(map[accapi.ProductCategoryIdV2]*internalBucket)

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

	resetProducts()
	createTestProduct(cpuProduct)
	createTestProduct(storageProduct)
	initGrants()
}

func TestPostCommentHappyPath(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)

	const giver = "giver-1"
	addGrantGiver(t, giver)

	alice := actor("alice", giver)

	req := accapi.GrantsSubmitRevisionRequest{
		Comment: "initial draft",
		Revision: accapi.GrantDocument{
			Recipient: accapi.Recipient{
				Type:     accapi.RecipientTypePersonalWorkspace,
				Username: util.OptValue(alice.Username),
			},
			AllocationRequests: []accapi.AllocationRequest{
				{
					Category:         cpuCategory.Name,
					Provider:         cpuCategory.Provider,
					GrantGiver:       giver,
					BalanceRequested: util.OptValue[int64](100),
					Period: accapi.Period{
						Start: util.OptValue(fndapi.Timestamp(time.Now())),
						End:   util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
					},
				},
			},
			AllocationPeriod: util.OptValue(accapi.Period{
				Start: util.OptValue(fndapi.Timestamp(time.Now())),
				End:   util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
			}),
			Form: accapi.Form{
				Type: accapi.FormTypePlainText,
				Text: "test application",
			},
		},
	}

	id64, err := GrantsSubmitRevision(*alice, req)
	assert.Nil(t, err)
	appID := strconv.FormatInt(id64, 10)

	_, err2 := GrantsPostComment(*alice, accapi.GrantsPostCommentRequest{
		ApplicationId: appID,
		Comment:       "test",
	})
	assert.Nil(t, err2)

	// ensure comment is stored
	app, httpErr := GrantsRetrieve(*alice, appID)
	assert.Nil(t, httpErr)
	assert.Equal(t, len(app.Status.Comments), 1)
	assert.Equal(t, "test", app.Status.Comments[0].Comment)
}

func TestBrowseNoDuplicates(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)

	const giver = "giver-2"
	addGrantGiver(t, giver)

	alice := actor("alice", giver) // submitter + admin(approver)
	bob := actor("bob", giver)     // plain approver

	// submit one application as Alice
	subReq := accapi.GrantsSubmitRevisionRequest{
		Comment: "draft",
		Revision: accapi.GrantDocument{
			Recipient: accapi.Recipient{
				Type:     accapi.RecipientTypePersonalWorkspace,
				Username: util.OptValue(alice.Username),
			},
			AllocationRequests: []accapi.AllocationRequest{
				{
					Category:         cpuCategory.Name,
					Provider:         cpuCategory.Provider,
					GrantGiver:       giver,
					BalanceRequested: util.OptValue[int64](100),
					Period: accapi.Period{
						Start: util.OptValue(fndapi.Timestamp(time.Now())),
						End:   util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
					},
				},
			},
			AllocationPeriod: util.OptValue(accapi.Period{
				End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
			}),
			Form: accapi.Form{
				Type: accapi.FormTypePlainText,
				Text: "foo",
			},
		},
	}

	_, err := GrantsSubmitRevision(*alice, subReq)
	assert.Nil(t, err)

	// Alice browses w/ both incoming & outgoing
	page := GrantsBrowse(*alice, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications:  util.OptValue(true),
		IncludeOutgoingApplications: util.OptValue(true),
		Filter:                      util.OptValue(accapi.GrantApplicationFilterShowAll),
	})
	assert.Equal(t, len(page.Items), 1, "application must not be duplicated")
	assert.Equal(t, alice.Username, page.Items[0].CreatedBy)

	// sanity: Bob (approver only) sees the same single app
	page2 := GrantsBrowse(*bob, accapi.GrantsBrowseRequest{
		IncludeOutgoingApplications: util.OptValue(true),
		Filter:                      util.OptValue(accapi.GrantApplicationFilterShowAll),
	})
	assert.Equal(t, len(page2.Items), 1)
}

func TestCannotMutateAfterApproval(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)

	const giver = "giver-3"
	addGrantGiver(t, giver)

	alice := actor("alice", "")    // submitter
	admin := actor("admin", giver) // approver

	// 1) Alice submits
	subReq := accapi.GrantsSubmitRevisionRequest{
		Comment: "draft",
		Revision: accapi.GrantDocument{
			Recipient: accapi.Recipient{
				Type:     accapi.RecipientTypePersonalWorkspace,
				Username: util.OptValue(alice.Username),
			},
			AllocationRequests: []accapi.AllocationRequest{
				{
					Category:         cpuCategory.Name,
					Provider:         cpuCategory.Provider,
					GrantGiver:       giver,
					BalanceRequested: util.OptValue[int64](100),
					Period: accapi.Period{
						End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
					},
				},
			},
			AllocationPeriod: util.OptValue(accapi.Period{
				End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
			}),
			Form: accapi.Form{
				Type: accapi.FormTypePlainText,
				Text: "foo",
			},
		},
	}

	appID64, err := GrantsSubmitRevision(*alice, subReq)
	assert.Nil(t, err)
	appID := strconv.FormatInt(appID64, 10)

	// 2) Giver approves
	err2 := GrantsUpdateState(*admin, accapi.GrantsUpdateStateRequest{
		ApplicationId: appID,
		NewState:      accapi.GrantApplicationStateApproved,
	})
	assert.Nil(t, err2)

	// 3) Alice tries to push a new revision -> must be rejected
	subReq2 := subReq
	subReq2.Comment = "second draft"
	subReq2.ApplicationId.Set(appID)

	_, err3 := GrantsSubmitRevision(*alice, subReq2)
	assert.NotNil(t, err3, "revision after approval must fail")
	assert.Equal(t, http.StatusBadRequest, err3.StatusCode)
}

func TestAwardedNotSetOnCreateProjectFailure(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	grantGlobals.Testing.ProjectCreateFailure = true // force failure

	initGrantsTest(t)

	const giver = "giver-4"
	addGrantGiver(t, giver)

	alice := actor("alice", "")

	// Prepare app struct directly and skip validation.
	// We only care about the flag behaviour here and want to avoid wallets).
	id := accGrantId(grantGlobals.GrantIdAcc.Add(1))
	app := &grantApplication{
		Application: &accapi.GrantApplication{
			Id:        strconv.FormatInt(int64(id), 10),
			CreatedBy: alice.Username,
			CreatedAt: fndapi.Timestamp(time.Now()),
			UpdatedAt: fndapi.Timestamp(time.Now()),
			CurrentRevision: accapi.GrantRevision{
				Document: accapi.GrantDocument{
					Recipient: accapi.Recipient{
						Type:  accapi.RecipientTypeNewProject,
						Title: util.OptValue("new-project-x"),
					},
					AllocationRequests: []accapi.AllocationRequest{},
					AllocationPeriod: util.OptValue(accapi.Period{
						End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0))),
					}),
				},
			},
			Status: accapi.GrantStatus{
				OverallState: accapi.GrantApplicationStateApproved,
			},
		},
	}

	b := grantGetAppBucket(id)
	b.Mu.Lock()
	b.Applications[id] = app
	b.Mu.Unlock()

	lGrantsAwardResources(app)
	assert.False(t, app.Awarded, "Awarded must stay false when project creation fails")
}

func TestBrowseFilterActiveInactive(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "giver-a"
	addGrantGiver(t, giver)
	alice := actor("alice", giver)
	_, err := GrantsSubmitRevision(*alice, rev(alice.Username, giver, 10))
	assert.Nil(t, err)
	id2, _ := GrantsSubmitRevision(*alice, rev(alice.Username, giver, 20))
	admin := actor("admin", giver)
	_ = GrantsUpdateState(*admin, accapi.GrantsUpdateStateRequest{
		ApplicationId: strconv.FormatInt(id2, 10),
		NewState:      accapi.GrantApplicationStateApproved,
	})
	active := GrantsBrowse(*alice, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications: util.OptValue(true),
		Filter:                     util.OptValue(accapi.GrantApplicationFilterActive),
	})
	inactive := GrantsBrowse(*alice, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications: util.OptValue(true),
		Filter:                     util.OptValue(accapi.GrantApplicationFilterInactive),
	})
	assert.Equal(t, 1, len(active.Items))
	assert.Equal(t, 1, len(inactive.Items))
	assert.NotEqual(t, active.Items[0].Id, inactive.Items[0].Id)
}

func TestCommentDeleteAuthorization(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "giver-b"
	addGrantGiver(t, giver)
	alice := actor("alice", giver)
	id64, _ := GrantsSubmitRevision(*alice, rev(alice.Username, giver, 5))
	appID := strconv.FormatInt(id64, 10)
	commentId, _ := GrantsPostComment(*alice, accapi.GrantsPostCommentRequest{
		ApplicationId: appID,
		Comment:       "z",
	})
	bob := actor("bob", "")
	err := GrantsDeleteComment(*bob, accapi.GrantsDeleteCommentRequest{
		ApplicationId: appID,
		CommentId:     commentId,
	})
	assert.NotNil(t, err)
	admin := actor("admin", giver)
	err2 := GrantsDeleteComment(*admin, accapi.GrantsDeleteCommentRequest{
		ApplicationId: appID,
		CommentId:     commentId,
	})
	assert.Nil(t, err2)
	app, _ := GrantsRetrieve(*alice, appID)
	assert.Equal(t, len(app.Status.Comments), 0)
}

func TestSelfAllocationRejected(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "giver-c"
	addGrantGiver(t, giver)
	alice := actor("alice", "")
	r := rev(alice.Username, giver, 10)
	r.Revision.Recipient = accapi.Recipient{
		Type: accapi.RecipientTypeExistingProject,
		Id:   util.OptValue(giver),
	}
	_, err := GrantsSubmitRevision(*alice, r)
	assert.NotNil(t, err)
	assert.Equal(t, http.StatusBadRequest, err.StatusCode)
}

func TestGrantTransfer(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const g1 = "giver-d1"
	const g2 = "giver-d2"
	addGrantGiver(t, g1)
	addGrantGiver(t, g2)
	alice := actor("alice", g1)
	req := rev(alice.Username, g1, 50)
	id64, _ := GrantsSubmitRevision(*alice, req)
	appID := strconv.FormatInt(id64, 10)
	admin := actor("admin", g1)
	err := GrantsTransfer(*admin, accapi.GrantsTransferRequest{
		ApplicationId: appID,
		Target:        g2,
	})
	assert.Nil(t, err)
	app, _ := GrantsRetrieve(*alice, appID)
	states := app.Status.StateBreakdown
	assert.Equal(t, 1, len(states))
	assert.Equal(t, g2, states[0].ProjectId)
}

func TestMultiGiverApproveReject(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	g1, g2 := "mg-g1", "mg-g2"
	addGrantGiver(t, g1)
	addGrantGiver(t, g2)
	sub := actor("sub", "")
	admin1 := actor("adm1", g1)
	admin2 := actor("adm2", g2)
	req := rev(sub.Username, g1, 10)
	req.Revision.AllocationRequests = append(req.Revision.AllocationRequests, accapi.AllocationRequest{
		Category:         cpuCategory.Name,
		Provider:         cpuCategory.Provider,
		GrantGiver:       g2,
		BalanceRequested: util.OptValue[int64](1000),
		Period:           accapi.Period{End: util.OptValue(fndapi.Timestamp(time.Now().AddDate(1, 0, 0)))},
	})
	id64, _ := GrantsSubmitRevision(*sub, req)
	appID := strconv.FormatInt(id64, 10)
	err := GrantsUpdateState(*admin1, accapi.GrantsUpdateStateRequest{ApplicationId: appID, NewState: accapi.GrantApplicationStateApproved})
	assert.Nil(t, err)
	app, _ := GrantsRetrieve(*sub, appID)
	assert.Equal(t, accapi.GrantApplicationStateInProgress, app.Status.OverallState)
	err2 := GrantsUpdateState(*admin2, accapi.GrantsUpdateStateRequest{ApplicationId: appID, NewState: accapi.GrantApplicationStateRejected})
	assert.Nil(t, err2)
	app2, _ := GrantsRetrieve(*sub, appID)
	assert.Equal(t, accapi.GrantApplicationStateRejected, app2.Status.OverallState)
	st := map[string]accapi.GrantApplicationState{}
	for _, s := range app2.Status.StateBreakdown {
		st[s.ProjectId] = s.State
	}
	assert.Equal(t, accapi.GrantApplicationStateApproved, st[g1])
	assert.Equal(t, accapi.GrantApplicationStateRejected, st[g2])
}

func TestUnauthorizedStateUpdate(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "unauth-g"
	addGrantGiver(t, giver)
	alice := actor("alice", "")
	admin := actor("admin", giver)
	id64, _ := GrantsSubmitRevision(*alice, rev(alice.Username, giver, 5))
	appID := strconv.FormatInt(id64, 10)
	err := GrantsUpdateState(*alice, accapi.GrantsUpdateStateRequest{ApplicationId: appID, NewState: accapi.GrantApplicationStateApproved})
	assert.NotNil(t, err)
	assert.Equal(t, http.StatusNotFound, err.StatusCode)
	assert.Nil(t, GrantsUpdateState(*admin, accapi.GrantsUpdateStateRequest{ApplicationId: appID, NewState: accapi.GrantApplicationStateApproved}))
}

func TestUserCriteriaAllowExclude(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "crit-g"
	addGrantGiver(t, giver)
	admin := actor("admin", giver)
	s := accapi.GrantRequestSettings{
		Enabled: true,
		AllowRequestsFrom: []accapi.UserCriteria{{
			Type:   accapi.UserCriteriaTypeEmail,
			Domain: util.OptValue("foo.com"),
		}},
		ExcludeRequestsFrom: []accapi.UserCriteria{{
			Type: accapi.UserCriteriaTypeWayf,
			Org:  util.OptValue("barOrg"),
		}},
		Templates: accapi.Templates{Type: accapi.TemplatesTypePlainText},
	}
	assert.Nil(t, GrantsUpdateSettings(*admin, giver, s))
	a1 := actor("u1", "")
	a1.Domain = "foo.com"
	a1.OrgId = "x"
	a2 := actor("u2", "")
	a2.Domain = "other.com"
	a3 := actor("u3", "")
	a3.Domain = "foo.com"
	a3.OrgId = "barOrg"
	req := accapi.RetrieveGrantGiversRequest{Type: accapi.RetrieveGrantGiversTypePersonalWorkspace}
	l1, _ := GrantsRetrieveGrantGivers(*a1, req)
	l2, _ := GrantsRetrieveGrantGivers(*a2, req)
	l3, _ := GrantsRetrieveGrantGivers(*a3, req)
	assert.Equal(t, 1, len(l1))
	assert.Equal(t, 0, len(l2))
	assert.Equal(t, 0, len(l3))
}

func TestBrowsePagination(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)
	const giver = "page-g"
	addGrantGiver(t, giver)
	u := actor("u", giver)
	for i := 0; i < 3; i++ {
		_, _ = GrantsSubmitRevision(*u, rev(u.Username, giver, int64(10+i)))
	}
	page1 := GrantsBrowse(*u, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications: util.OptValue(true),
		ItemsPerPage:               1,
	})
	assert.Equal(t, 1, len(page1.Items))
	assert.True(t, page1.Next.Present)
	page2 := GrantsBrowse(*u, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications: util.OptValue(true),
		ItemsPerPage:               1,
		Next:                       page1.Next,
	})
	assert.Equal(t, 1, len(page2.Items))
	assert.True(t, page2.Next.Present)
	page3 := GrantsBrowse(*u, accapi.GrantsBrowseRequest{
		IncludeIngoingApplications: util.OptValue(true),
		ItemsPerPage:               1,
		Next:                       page2.Next,
	})
	assert.Equal(t, 1, len(page3.Items))
	assert.False(t, page3.Next.Present)
}

func TestGrantGiverUpdateAndRetrieve(t *testing.T) {
	grantGlobals.Testing.Enabled = true
	initGrantsTest(t)

	const giver = "sett-g"
	addGrantGiver(t, giver)
	admin := actor("admin", giver)

	newSettings := accapi.GrantRequestSettings{
		Enabled:     true,
		Description: "updated desc",
		AllowRequestsFrom: []accapi.UserCriteria{
			{Type: accapi.UserCriteriaTypeAnyone},
		},
		Templates: accapi.Templates{Type: accapi.TemplatesTypePlainText},
	}
	assert.Nil(t, GrantsUpdateSettings(*admin, giver, newSettings))

	alice := actor("alice", "")
	alice.Domain = "foo.com"

	list, _ := GrantsRetrieveGrantGivers(*alice, accapi.RetrieveGrantGiversRequest{
		Type: accapi.RetrieveGrantGiversTypePersonalWorkspace,
	})

	found := false
	for _, g := range list {
		if g.Id == giver {
			found = true
			assert.Equal(t, "updated desc", g.Description)
		}
	}
	assert.True(t, found)
}
