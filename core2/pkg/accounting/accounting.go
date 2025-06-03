package accounting

import (
	"fmt"
	"net/http"
	"strings"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAccounting() {
	accountingLoad()
	go accountingProcessTasks()
}

func RootAllocate(actor rpc.Actor, request accapi.RootAllocateRequest) (string, *util.HttpError) {
	if actor.Role&rpc.RolesEndUser == 0 {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	if !actor.Project.Present {
		return "", util.HttpErr(http.StatusForbidden, "Cannot perform a root allocation in a personal workspace!")
	}

	projectId, ok := actor.ProviderProjects[rpc.ProviderId(request.Category.Provider)]

	if !ok || string(projectId) != actor.Project.Value {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	role := fndapi.ProjectRole(actor.Membership[projectId])
	if !role.Satisfies(fndapi.ProjectRoleAdmin) {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	category, err := ProductCategoryRetrieve(actor, request.Category.Name, request.Category.Provider)

	if err != nil {
		return "", util.HttpErr(http.StatusForbidden, "This category does not exist")
	}

	now := time.Now()

	bucket := internalBucketOrInit(category)
	recipientOwner := internalOwnerByReference(actor.Project.Value)
	recipient := internalWalletByOwner(bucket, now, recipientOwner.Id)

	id, err := internalAllocate(
		now,
		bucket,
		request.Start.Time(),
		request.End.Time(),
		request.Quota,
		recipient,
		internalGraphRoot,
		util.OptNone[accGrantId](),
	)

	return fmt.Sprint(id), err
}

func ReportUsage(actor rpc.Actor, request accapi.ReportUsageRequest) (bool, *util.HttpError) {
	providerId, ok := strings.CutPrefix(fndapi.ProviderSubjectPrefix, actor.Username)
	if !ok {
		return false, util.HttpErr(http.StatusForbidden, "You cannot report usage")
	}

	if providerId != request.CategoryIdV2.Provider {
		return false, util.HttpErr(http.StatusForbidden, "You cannot report usage for this product")
	}

	_, err := ProductCategoryRetrieve(actor, request.CategoryIdV2.Name, request.CategoryIdV2.Provider)

	if err != nil {
		return false, util.HttpErr(http.StatusForbidden, "This category does not exist")
	}

	if !validateOwner(request.Owner) {
		return false, util.HttpErr(http.StatusForbidden, "This user/project does not exist")
	}

	if !request.IsDeltaCharge && request.Usage < 0 {
		return false, util.HttpErr(http.StatusForbidden, "Absolute usage cannot be negative")
	}

	success, err := internalReportUsage(time.Now(), request)
	return success, err
}

func validateOwner(owner accapi.WalletOwner) bool {
	return false // TODO
}

func accountingLoad() {
	// TODO
}

func accountingProcessTasks() {
	for {
		now := time.Now()
		internalCompleteScan(now, func(buckets []*internalBucket, scopes []*scopedUsage) {
			// TODO save stuff to db
		})
		time.Sleep(30 * time.Second)
	}
}
