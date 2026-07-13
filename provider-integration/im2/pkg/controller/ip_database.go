package controller

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"math/big"
	"net"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

// This file contains an in-memory version of all public IPs. It is similar to the job_database.go file. This file is
// only capable of operating in server mode with no user-mode functionality. This will be changed if and when we need
// it. The file is initialized by the job_database and often controlled by the job_database in response to job events.
// This file also exposes several IPC calls and CLI stub to manage a pool of available (external) IP addresses.

var publicIps struct {
	Mu                     sync.Mutex
	Ips                    map[string]*orc.PublicIp
	Pool                   []publicIpPoolEntry
	ExternalAddressesInUse map[string]string // Textual IP (net.IP) to orc.PublicIp identifier
}

type publicIpPoolEntry struct {
	Public  net.IPNet
	Private net.IPNet
}

type ipcAddToPoolRequest struct {
	Public  string
	Private string
}

type IpReclaimPlanItem struct {
	ResourceId  string
	IpAddress   string
	Owner       string
	UnusedSince time.Time
}

type ipReclaimPreviewRequest struct {
	UnusedForMillis int64
}

type ipReclaimPreviewResponse struct {
	PlanId string
	Items  []IpReclaimPlanItem
}

type IpReclaimResult struct {
	IpReclaimPlanItem
	Error string
}

var (
	ipcRetrieveIpPool = ipc.NewCall[util.Empty, []IpPoolEntry]("publicIps.retrievePool")
	ipcAddToPool      = ipc.NewCall[ipcAddToPoolRequest, util.Empty]("publicIps.addToPool")
	ipcRemoveFromPool = ipc.NewCall[string, util.Empty]("publicIps.removeFromPool")
	ipcPreviewReclaim = ipc.NewCall[ipReclaimPreviewRequest, ipReclaimPreviewResponse]("publicIps.reclaim.preview")
	ipcExecuteReclaim = ipc.NewCall[string, []IpReclaimResult]("publicIps.reclaim.execute")
	ipcReclaimIps     = ipc.NewCall[[]string, []IpReclaimResult]("publicIps.reclaim.ids")
)

// initIpDatabase is invoked by the job_database
func initIpDatabase() {
	if !RunsServerCode() {
		return
	}

	publicIps.Mu.Lock()
	publicIps.Ips = make(map[string]*orc.PublicIp)
	publicIps.ExternalAddressesInUse = make(map[string]string)
	publicIpFetchAll()
	publicIps.Mu.Unlock()

	publicIpLoadPool()

	ipcAddToPool.Handler(func(r *ipc.Request[ipcAddToPoolRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		err := IpPoolAddSubnet(r.Payload.Public, r.Payload.Private)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	ipcRemoveFromPool.Handler(func(r *ipc.Request[string]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		err := IpPoolRemoveSubnet(r.Payload)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	ipcRetrieveIpPool.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]IpPoolEntry] {
		if r.Uid != 0 {
			return ipc.Response[[]IpPoolEntry]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		pool := IpPoolRetrieveAll()
		return ipc.Response[[]IpPoolEntry]{
			StatusCode: http.StatusOK,
			Payload:    pool,
		}
	})

	ipcPreviewReclaim.Handler(func(r *ipc.Request[ipReclaimPreviewRequest]) ipc.Response[ipReclaimPreviewResponse] {
		if r.Uid != 0 {
			return ipc.Response[ipReclaimPreviewResponse]{StatusCode: http.StatusForbidden, ErrorMessage: "You must be root to run this command"}
		}
		result, err := PublicIpReclaimPreview(time.Duration(r.Payload.UnusedForMillis) * time.Millisecond)
		if err != nil {
			return ipc.Response[ipReclaimPreviewResponse]{StatusCode: http.StatusBadRequest, ErrorMessage: err.Error()}
		}
		return ipc.Response[ipReclaimPreviewResponse]{StatusCode: http.StatusOK, Payload: result}
	})

	ipcExecuteReclaim.Handler(func(r *ipc.Request[string]) ipc.Response[[]IpReclaimResult] {
		if r.Uid != 0 {
			return ipc.Response[[]IpReclaimResult]{StatusCode: http.StatusForbidden, ErrorMessage: "You must be root to run this command"}
		}
		result, err := PublicIpReclaimExecute(r.Payload)
		if err != nil {
			return ipc.Response[[]IpReclaimResult]{StatusCode: http.StatusBadRequest, ErrorMessage: err.Error()}
		}
		return ipc.Response[[]IpReclaimResult]{StatusCode: http.StatusOK, Payload: result}
	})

	ipcReclaimIps.Handler(func(r *ipc.Request[[]string]) ipc.Response[[]IpReclaimResult] {
		if r.Uid != 0 {
			return ipc.Response[[]IpReclaimResult]{StatusCode: http.StatusForbidden, ErrorMessage: "You must be root to run this command"}
		}
		return ipc.Response[[]IpReclaimResult]{StatusCode: http.StatusOK, Payload: PublicIpReclaimIds(r.Payload)}
	})
}

func publicIpSetUnusedSince(id string, when util.Option[time.Time]) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`update tracked_ips set unused_since = :unused_since where resource_id = :id`,
			db.Params{"id": id, "unused_since": when.Sql()},
		)
	})
}

func publicIpFetchAll() {
	next := ""

	for {
		request := orc.PublicIpsControlBrowseRequest{Next: util.OptStringIfNotEmpty(next)}
		request.IncludeProduct = false
		request.IncludeUpdates = true
		page, err := orc.PublicIpsControlBrowse.Invoke(request)

		if err != nil {
			log.Warn("Failed to fetch public ips: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			ip := &page.Items[i]
			publicIps.Ips[ip.Id] = ip
			if ip.Status.IpAddress.Present {
				publicIps.ExternalAddressesInUse[ip.Status.IpAddress.Value] = ip.Id
			}
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}

func publicIpLoadPool() {
	rows := db.NewTx(func(tx *db.Transaction) []struct {
		Subnet        string
		PrivateSubnet string
	} {
		return db.Select[struct {
			Subnet        string
			PrivateSubnet string
		}](
			tx,
			`
				select subnet, private_subnet
				from ip_pool
		    `,
			db.Params{},
		)
	})

	for _, row := range rows {
		_, parsedNet, err := net.ParseCIDR(row.Subnet)
		if err != nil || parsedNet == nil {
			log.Warn("Could not load subnet '%s': %s", row.Subnet, err)
			continue
		}

		_, parsedPrivateNet, err := net.ParseCIDR(row.PrivateSubnet)
		if err != nil || parsedPrivateNet == nil {
			log.Warn("Could not load subnet '%s': %s", row.Subnet, err)
			continue
		}

		publicIps.Pool = append(publicIps.Pool, publicIpPoolEntry{
			Public:  *parsedNet,
			Private: *parsedPrivateNet,
		})

		log.Info("Loaded %v", row.Subnet)
	}

	ipsInUse := db.NewTx(func(tx *db.Transaction) []struct {
		ResourceId string
		IpAddress  string
	} {
		return db.Select[struct {
			ResourceId string
			IpAddress  string
		}](
			tx,
			`
				with ips as (select resource_id, resource->'status'->>'ipAddress' as ip_address from tracked_ips)
				select resource_id, ip_address from ips where ip_address is not null
		    `,
			db.Params{},
		)
	})

	var toDelete []string
	publicIps.Mu.Lock()
	for _, ipInUse := range ipsInUse {
		if ipInUse.IpAddress == "invalid" {
			log.Info("Want to delete: %s", ipInUse.ResourceId)
			toDelete = append(toDelete, ipInUse.ResourceId)
		} else {
			log.Info("OK: %s", ipInUse.IpAddress)
			publicIps.ExternalAddressesInUse[ipInUse.IpAddress] = ipInUse.ResourceId
		}
	}
	publicIps.Mu.Unlock()

	if len(toDelete) > 0 {
		go func() {
			time.Sleep(2 * time.Second)
			for _, id := range toDelete {
				ip, ok := PublicIpRetrieve(id)
				if ok {
					deleteFn := Jobs.PublicIPs.Delete
					if deleteFn != nil {
						err := deleteFn(ip)
						if err != nil {
							log.Info("Could not delete IP %s: %s", id, err)
						}
					} else {
						err := PublicIpDelete(ip)
						log.Info("Could not delete IP %s: %s", id, err)
					}
				} else {
					log.Info("Could not delete IP %s", id)
				}
			}
		}()
	}
}

func PublicIpTrackNew(ip orc.PublicIp) {
	// Automatically assign timestamps to all updates that do not have one.
	for i := 0; i < len(ip.Updates); i++ {
		update := &ip.Updates[i]
		if update.Timestamp.UnixMilli() <= 0 {
			update.Timestamp = fnd.Timestamp(time.Now())
		}

		if update.ChangeIpAddress.GetOrDefault(false) {
			ip.Status.IpAddress = update.NewIpAddress
		}
	}

	publicIps.Mu.Lock()
	{
		existingIp, ok := publicIps.Ips[ip.Id]
		publicIps.Ips[ip.Id] = &ip

		if ok {
			existingIpAddress := existingIp.Status.IpAddress
			if existingIpAddress.Present {
				delete(publicIps.ExternalAddressesInUse, existingIpAddress.Value)
			}
		}

		if ip.Status.IpAddress.Present {
			publicIps.ExternalAddressesInUse[ip.Status.IpAddress.Value] = ip.Id
		}
	}
	publicIps.Mu.Unlock()

	jsonified, _ := json.Marshal(ip)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_ips(resource_id, created_by, project_id, product_id, product_category, resource)
				values (:resource_id, :created_by, :project_id, :product_id, :product_category, :resource)
				on conflict (resource_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id,
					product_id = excluded.product_id,
					product_category = excluded.product_category
			`,
			db.Params{
				"resource_id":      ip.Id,
				"created_by":       ip.Owner.CreatedBy,
				"project_id":       ip.Owner.Project.Value,
				"product_id":       ip.Specification.Product.Id,
				"product_category": ip.Specification.Product.Category,
				"resource":         string(jsonified),
			},
		)
	})
}

type IpPoolEntry struct {
	Subnet        string
	PrivateSubnet string
	Allocated     int
	Remaining     int
}

func IpPoolRetrieveAll() []IpPoolEntry {
	publicIps.Mu.Lock()
	defer publicIps.Mu.Unlock()

	var result []IpPoolEntry

	for _, subnet := range publicIps.Pool {
		included, maskSize := subnet.Public.Mask.Size()
		ipsInSubnet := 1 << int64(maskSize-included)
		numAllocated := 0

		for allocated, _ := range publicIps.ExternalAddressesInUse {
			parsed := net.ParseIP(allocated)
			if parsed != nil && subnet.Public.Contains(parsed) {
				numAllocated++
			}
		}

		result = append(result, IpPoolEntry{
			Subnet:        subnet.Public.String(),
			PrivateSubnet: subnet.Private.String(),
			Allocated:     numAllocated,
			Remaining:     ipsInSubnet - numAllocated,
		})
	}

	return result
}

func PublicIpAllocate(target *orc.PublicIp) *util.HttpError {
	if target == nil {
		return util.ServerHttpError("target is nil")
	}

	publicIps.Mu.Lock()

	allocatedIp := util.Option[string]{}

	// NOTE(Dan): Performance of this code heavily degrades as we get a lot of IP addresses. This is not a problem we
	// have today. In case we ever get a large block of IPs, someone should update the code to actually be smart
	// about allocation.
outer:
	for _, subnet := range publicIps.Pool {
		included, maskSize := subnet.Public.Mask.Size()
		ipsInSubnet := 1 << int64(maskSize-included)

		numericIp := &big.Int{}
		numericIp.SetBytes(subnet.Public.IP)

		for i := 0; i < ipsInSubnet; i++ {
			toAdd := big.NewInt(int64(i))

			newNumericIp := &big.Int{}
			newNumericIp.Add(numericIp, toAdd)

			newIp := net.IP(newNumericIp.Bytes())
			if len(newIp) == net.IPv4len && (newIp[3] == 0 || newIp[3] == 1 || newIp[3] == 255) {
				continue
			}

			newIpString := newIp.String()
			_, exists := publicIps.ExternalAddressesInUse[newIpString]
			if !exists {
				allocatedIp.Set(newIpString)
				publicIps.ExternalAddressesInUse[newIpString] = target.Id
				break outer
			}
		}
	}

	publicIps.Mu.Unlock()

	if !allocatedIp.Present {
		_ = PublicIpDelete(target)
		return util.HttpErr(http.StatusBadRequest, "%s has no more IP addresses available", cfg.Provider.Id)
	} else {
		newUpdate := orc.PublicIpUpdate{
			State:           util.OptValue(orc.PublicIpStateReady),
			ChangeIpAddress: util.OptValue(true),
			NewIpAddress:    util.OptValue(allocatedIp.Value),
			Timestamp:       fnd.Timestamp(time.Now()),
		}

		_, err := orc.PublicIpsControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.PublicIpUpdate]]{
			Items: []orc.ResourceUpdateAndId[orc.PublicIpUpdate]{
				{
					Id:     target.Id,
					Update: newUpdate,
				},
			},
		})

		if err == nil {
			target.Updates = append(target.Updates, newUpdate)
			PublicIpTrackNew(*target)
			publicIpSetUnusedSince(target.Id, util.OptValue(time.Now()))
			return nil
		} else {
			log.Info("Failed to allocate an IP address due to an error between UCloud and the provider: %s", err)

			_ = PublicIpDelete(target)
			return err
		}
	}
}

func PublicIpDelete(address *orc.PublicIp) *util.HttpError {
	// The provider delete payload can be stale while a job is being started. Core is
	// authoritative for bindings, so check it once more before releasing this address.
	fresh, err := orc.PublicIpsControlRetrieve.Invoke(orc.PublicIpsControlRetrieveRequest{Id: address.Id})
	if err != nil {
		return err
	}
	address = &fresh
	if len(address.Status.BoundTo) > 0 {
		return util.UserHttpError("This IP is currently in use by job: %v", strings.Join(address.Status.BoundTo, ", "))
	}

	publicIps.Mu.Lock()

	if address.Status.IpAddress.Present {
		delete(publicIps.ExternalAddressesInUse, address.Status.IpAddress.Value)
	}

	delete(publicIps.Ips, address.Id)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_ips
				where resource_id = :id
		    `,
			db.Params{
				"id": address.Id,
			},
		)
	})

	publicIps.Mu.Unlock()
	return nil
}

func PublicIpReclaimPreview(unusedFor time.Duration) (ipReclaimPreviewResponse, error) {
	if unusedFor <= 0 {
		return ipReclaimPreviewResponse{}, fmt.Errorf("--unused-for must be greater than zero")
	}
	type ipRow struct {
		Resource    string
		UnusedSince time.Time
	}
	tracked := db.NewTx(func(tx *db.Transaction) []ipRow {
		return db.Select[ipRow](
			tx,
			`select resource, unused_since from tracked_ips where unused_since is not null`,
			db.Params{},
		)
	})

	var candidates []IpReclaimPlanItem
	for _, row := range tracked {
		var ip orc.PublicIp
		if json.Unmarshal([]byte(row.Resource), &ip) != nil || len(ip.Status.BoundTo) != 0 || !ip.Status.IpAddress.Present || time.Since(row.UnusedSince) < unusedFor {
			continue
		}
		owner := ip.Owner.CreatedBy
		if ip.Owner.Project.Present {
			owner = ip.Owner.Project.Value
		}
		candidates = append(candidates, IpReclaimPlanItem{ResourceId: ip.Id, IpAddress: ip.Status.IpAddress.Value, Owner: owner, UnusedSince: row.UnusedSince})
	}

	planId := util.SecureToken()
	now := time.Now()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from ip_reclaim_plans where expires_at < now()`,
			db.Params{},
		)

		db.Exec(
			tx,
			`
				insert into ip_reclaim_plans(id, created_at, expires_at, unused_for_ms)
				values (:id, :created_at, :expires_at, :unused_for_ms)
			`,
			db.Params{
				"id": planId, "created_at": now, "expires_at": now.Add(24 * time.Hour), "unused_for_ms": unusedFor.Milliseconds(),
			},
		)

		for _, item := range candidates {
			db.Exec(
				tx,
				`
					insert into ip_reclaim_plan_items(plan_id, resource_id, ip_address, owner, unused_since)
					values (:plan_id, :resource_id, :ip_address, :owner, :unused_since)`,
				db.Params{
					"plan_id": planId, "resource_id": item.ResourceId, "ip_address": item.IpAddress, "owner": item.Owner, "unused_since": item.UnusedSince,
				},
			)
		}
	})
	return ipReclaimPreviewResponse{PlanId: planId, Items: candidates}, nil
}

func PublicIpReclaimExecute(planId string) ([]IpReclaimResult, error) {
	if planId == "" {
		return nil, fmt.Errorf("missing reclaim plan ID")
	}

	type planRow struct {
		ResourceId  string
		IpAddress   string
		Owner       string
		UnusedSince time.Time
	}

	items := db.NewTx(func(tx *db.Transaction) []planRow {
		return db.Select[planRow](
			tx,
			`
				select i.resource_id, i.ip_address, i.owner, i.unused_since
				from
				    ip_reclaim_plan_items i
					join ip_reclaim_plans p on p.id = i.plan_id
				where
				    p.id = :id and p.expires_at >= now()
			`,
			db.Params{"id": planId},
		)
	})

	if len(items) == 0 {
		return nil, fmt.Errorf("reclaim plan not found, expired, or empty")
	}

	result := make([]IpReclaimResult, 0, len(items))
	for _, item := range items {
		r := IpReclaimResult{
			IpReclaimPlanItem: IpReclaimPlanItem{
				ResourceId:  item.ResourceId,
				IpAddress:   item.IpAddress,
				Owner:       item.Owner,
				UnusedSince: item.UnusedSince,
			},
		}

		current := db.NewTx(func(tx *db.Transaction) time.Time {
			row, _ := db.Get[struct {
				UnusedSince time.Time
			}](
				tx,
				`
					select coalesce(unused_since, to_timestamp(0)) as unused_since
					from tracked_ips where resource_id = :id
				`,
				db.Params{"id": item.ResourceId},
			)
			return row.UnusedSince
		})
		if !current.Equal(item.UnusedSince) {
			r.Error = "IP activity changed since the preview"
			result = append(result, r)
			continue
		}
		_, err := orc.PublicIpsControlReclaim.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{Id: item.ResourceId}))
		if err != nil {
			r.Error = err.Error()
		}
		result = append(result, r)
	}
	return result, nil
}

func PublicIpReclaimIds(ids []string) []IpReclaimResult {
	result := make([]IpReclaimResult, 0, len(ids))
	for _, id := range ids {
		r := IpReclaimResult{IpReclaimPlanItem: IpReclaimPlanItem{ResourceId: id}}
		_, err := orc.PublicIpsControlReclaim.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{Id: id}))
		if err != nil {
			r.Error = err.Error()
		}
		result = append(result, r)
	}
	return result
}

func PublicIpRetrieveUsedCount(owner orc.ResourceOwner) int {
	return db.NewTx[int](func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from tracked_ips
				where
					(
						coalesce(:project, '') = '' 
						and coalesce(project_id, '') = '' 
						and created_by = :created_by
					)
					or (
						:project != '' 
						and project_id = :project
					);
		    `,
			db.Params{
				"created_by": owner.CreatedBy,
				"project":    owner.Project.Value,
			},
		)
		return row.Count
	})
}

func IpPoolAddSubnet(subnet string, privateSubnet string) error {
	ip, parsedSubnet, err := net.ParseCIDR(subnet)
	if err != nil || parsedSubnet == nil {
		return fmt.Errorf("invalid subnet specified: %s", err)
	}

	if _, maskSize := parsedSubnet.Mask.Size(); maskSize == 0 {
		// I don't think ParseCIDR will do this, but just in case.
		return fmt.Errorf("invalid subnet specified: non-canonical subnet specified")
	}

	_, parsedPrivateSubnet, err := net.ParseCIDR(privateSubnet)
	if err != nil || parsedPrivateSubnet == nil {
		return fmt.Errorf("invalid subnet specified: %s", err)
	}

	_, publicMaskSize := parsedSubnet.Mask.Size()
	_, privateMaskSize := parsedPrivateSubnet.Mask.Size()

	if publicMaskSize == 0 || privateMaskSize == 0 {
		// I don't think ParseCIDR will do this, but just in case.
		return fmt.Errorf("invalid subnet specified: non-canonical subnet specified")
	}

	if publicMaskSize != privateMaskSize {
		return fmt.Errorf("subnets must be of equal size")
	}

	publicIps.Mu.Lock()

	for _, existingSubnet := range publicIps.Pool {
		if existingSubnet.Public.Contains(ip) || parsedSubnet.Contains(existingSubnet.Public.IP) {
			err = fmt.Errorf("subnet overlaps with existing subnet (%s is in %s)", parsedSubnet.String(), existingSubnet.Public.String())
			break
		}
	}

	if err == nil {
		publicIps.Pool = append(publicIps.Pool, publicIpPoolEntry{
			Public:  *parsedSubnet,
			Private: *parsedPrivateSubnet,
		})
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into ip_pool(subnet, private_subnet)
					values (:subnet, :private_subnet) on conflict do nothing 
			    `,
				db.Params{
					"subnet":         subnet,
					"private_subnet": privateSubnet,
				},
			)
		})
	}

	publicIps.Mu.Unlock()

	return err
}

func IpPoolRemoveSubnet(subnet string) error {
	_, parsedSubnet, err := net.ParseCIDR(subnet)
	if err != nil || parsedSubnet == nil {
		return fmt.Errorf("invalid subnet specified: %s", err)
	}
	parsedString := parsedSubnet.String()

	publicIps.Mu.Lock()
	defer publicIps.Mu.Unlock()

	subnetIdx := -1
	for i, existingSubnet := range publicIps.Pool {
		if existingSubnet.Public.String() == subnet || existingSubnet.Public.String() == parsedString {
			subnetIdx = i
			break
		}
	}

	if subnetIdx == -1 {
		return fmt.Errorf("subnet not in pool (partial removes are not possible)")
	}

	var toInvalidate []string
	var runningJobs []string

	for _, ip := range publicIps.Ips {
		if !ip.Status.IpAddress.Present {
			continue
		}

		ipAddress := net.ParseIP(ip.Status.IpAddress.Value)
		if ipAddress == nil {
			continue
		}

		if parsedSubnet.Contains(ipAddress) {
			toInvalidate = append(toInvalidate, ip.Status.IpAddress.Value)
			if len(ip.Status.BoundTo) > 0 {
				for _, jobId := range ip.Status.BoundTo {
					runningJobs = append(runningJobs, jobId)
				}
			}
		}
	}

	if len(toInvalidate) > 0 {
		return fmt.Errorf(
			"unable to remove subnet from pool - the following IPs are bound: %s",
			strings.Join(toInvalidate, ", "),
		)
	}

	if len(runningJobs) > 0 {
		return fmt.Errorf(
			"unable to remove subnet - the following jobs use a bound IP: %s",
			strings.Join(runningJobs, ", "),
		)
	}

	// TODO(Dan): Once the Core has an API to delete an IP address from the provider, use it to forcefully clean it up.

	publicIps.Pool = util.RemoveAtIndex(publicIps.Pool, subnetIdx)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
					delete from ip_pool
					where subnet = :subnet
			    `,
			db.Params{
				"subnet": subnet,
			},
		)
	})

	return nil
}

func PublicIpBindToJob(job *orc.Job) ([]orc.PublicIp, []net.IP, error) {
	publicIps.Mu.Lock()

	var result []orc.PublicIp
	var privateIps []net.IP

	var err error

	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps.Ips[v.Id]
			if ok {
				result = append(result, *ip)
			} else {
				err = util.ServerHttpError("Could not bind IP address: %s", v.Id)
			}
		}
	}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps.Ips[v.Id]
			if ok {
				result = append(result, *ip)
			} else {
				err = util.ServerHttpError("Could not bind IP address: %s", v.Id)
			}
		}
	}

	if err == nil {
		for i := 0; i < len(result); i++ {
			ip := &result[i]

			if !ip.Status.IpAddress.Present {
				err = util.ServerHttpError(
					"IP %v is not ready yet - Try recreating it",
					ip.Id,
				).AsError()
				break
			}

			if len(ip.Status.BoundTo) > 0 {
				for _, jobId := range ip.Status.BoundTo {
					boundtoJob, ok := JobRetrieve(jobId)
					if jobId != job.Id && ok && !boundtoJob.Status.State.IsFinal() {
						err = util.UserHttpError(
							"%s (%v) is already bound to job %s",
							ip.Status.IpAddress.Value,
							ip.Id,
							strings.Join(ip.Status.BoundTo, ", "),
						).AsError()
						break
					}
				}

				if err != nil {
					break
				}
			}

			ip.Status.BoundTo = []string{job.Id}

			publicIp := net.ParseIP(ip.Status.IpAddress.Value)
			privateIp := publicIp
			for _, poolEntry := range publicIps.Pool {
				if poolEntry.Public.Contains(publicIp) {
					privateIp, err = IpRemapAddress(publicIp, poolEntry.Public, poolEntry.Private)
				}
			}
			privateIps = append(privateIps, privateIp)
		}
	}

	publicIps.Mu.Unlock() // Need to unlock before PublicIpTrackNew

	if err != nil {
		return nil, nil, err
	} else {
		for _, ip := range result {
			PublicIpTrackNew(ip)
			publicIpSetUnusedSince(ip.Id, util.OptNone[time.Time]())
		}

		return result, privateIps, nil
	}
}

// IpRemapAddress maps an IPv4 address from one subnet to an equal-sized destination subnet.
//
// - ip          – any host address you want to relocate
// - src, dst    – the *net.IPNet describing the source and destination subnets
//
// The two subnets must have identical prefix lengths (/24, /20)
func IpRemapAddress(ip net.IP, src, dst net.IPNet) (net.IP, error) {
	ip = ip.To4()
	if ip == nil {
		return nil, errors.New("RemapAddress works only with IPv4")
	}

	srcOnes, _ := src.Mask.Size()
	dstOnes, _ := dst.Mask.Size()
	if srcOnes != dstOnes {
		return nil, errors.New("source subnet must be the same size as destination subnet")
	}

	if !src.Contains(ip) {
		return nil, errors.New("address is not inside the source subnet")
	}

	ipInt := binary.BigEndian.Uint32(ip)
	dstNet := binary.BigEndian.Uint32(dst.IP.To4())
	mask := binary.BigEndian.Uint32(src.Mask)

	hostBits := ipInt & ^mask
	remapped := dstNet | hostBits

	return net.IPv4(
		byte(remapped>>24),
		byte(remapped>>16),
		byte(remapped>>8),
		byte(remapped),
	), nil
}

func PublicIpUnbindFromJob(job *orc.Job) {
	publicIps.Mu.Lock()

	var result []orc.PublicIp
	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps.Ips[v.Id]
			if ok {
				result = append(result, *ip)
			}
		}
	}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps.Ips[v.Id]
			if ok {
				result = append(result, *ip)
			}
		}
	}

	for i := 0; i < len(result); i++ {
		ip := &result[i]

		ipUpdate := orc.PublicIpUpdate{
			Binding: util.OptValue(orc.JobBinding{
				Kind: orc.JobBindingKindUnbind,
				Job:  job.Id,
			}),
		}

		ip.Updates = append(ip.Updates, ipUpdate)
		ip.Status.BoundTo = nil
	}

	publicIps.Mu.Unlock() // Need to unlock before PublicIpTrackNew

	for _, ip := range result {
		PublicIpTrackNew(ip)
		publicIpSetUnusedSince(ip.Id, util.OptValue(time.Now()))
	}
}

func IpPoolCliStub(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
		return
	}

	switch {
	case args[0] == "reclaim":
		ipReclaimCli(args[1:])
	case cli.IsListCommand(args[0]):
		pool, err := ipcRetrieveIpPool.Invoke(util.Empty{})
		cli.HandleError("listing IP pool", err)

		table := &termio.Table{}
		table.AppendHeader("Subnet")
		table.AppendHeader("Allocated")
		table.AppendHeader("Remaining")

		for _, row := range pool {
			table.Cell("%v", row.Subnet)
			table.Cell("%v", row.Allocated)
			table.Cell("%v", row.Remaining)
		}

		table.Print()

	case cli.IsAddCommand(args[0]):
		subnet := util.GetOptionalElement(args, 1)
		var err error
		if !subnet.Present {
			err = fmt.Errorf("missing subnet argument")
		} else {
			privateSubnet := util.GetOptionalElement(args, 2)
			if !privateSubnet.Present {
				privateSubnet.Set(subnet.Value)
			}
			_, err = ipcAddToPool.Invoke(ipcAddToPoolRequest{
				Public:  subnet.Value,
				Private: privateSubnet.Value,
			})
		}

		cli.HandleError("adding to IP pool", err)

	case cli.IsDeleteCommand(args[0]):
		subnet := util.GetOptionalElement(args, 1)
		var err error
		if !subnet.Present {
			err = fmt.Errorf("missing subnet argument")
		} else {
			_, err = ipcRemoveFromPool.Invoke(subnet.Value)
		}

		cli.HandleError("removing from IP pool", err)

	default:
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
	}
}

func ipReclaimCli(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown reclaim command")
		return
	}
	switch args[0] {
	case "preview":
		fs := flag.NewFlagSet("ips reclaim preview", flag.ExitOnError)
		unusedFor := fs.String("unused-for", "", "Minimum continuous unused period (for example 30d)")
		_ = fs.Parse(args[1:])
		duration, err := parseIpReclaimDuration(*unusedFor)
		cli.HandleError("parsing unused period", err)
		response, err := ipcPreviewReclaim.Invoke(ipReclaimPreviewRequest{UnusedForMillis: duration.Milliseconds()})
		cli.HandleError("previewing IP reclaim", err)
		termio.WriteStyledLine(termio.Bold, 0, 0, "Reclaim plan: %s (expires in 24 hours)", response.PlanId)
		ipReclaimTable(response.Items, nil)
	case "execute":
		planId := util.GetOptionalElement(args, 1)
		if !planId.Present {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing reclaim plan ID")
			return
		}
		results, err := ipcExecuteReclaim.Invoke(planId.Value)
		cli.HandleError("executing IP reclaim", err)
		items := make([]IpReclaimPlanItem, 0, len(results))
		errors := map[string]string{}
		for _, result := range results {
			items = append(items, result.IpReclaimPlanItem)
			errors[result.ResourceId] = result.Error
		}
		ipReclaimTable(items, errors)
	default:
		results, err := ipcReclaimIps.Invoke(args)
		cli.HandleError("reclaiming IPs", err)
		ipReclaimResultsTable(results)
	}
}

var ipReclaimDayUnit = regexp.MustCompile(`([0-9]+(?:\.[0-9]+)?)d`)

func parseIpReclaimDuration(value string) (time.Duration, error) {
	if value == "" {
		return 0, fmt.Errorf("--unused-for must be supplied")
	}

	value = ipReclaimDayUnit.ReplaceAllStringFunc(value, func(match string) string {
		days, err := strconv.ParseFloat(strings.TrimSuffix(match, "d"), 64)
		if err != nil {
			return match
		}
		return strconv.FormatFloat(days*24, 'f', -1, 64) + "h"
	})
	return time.ParseDuration(value)
}

func ipReclaimTable(items []IpReclaimPlanItem, errors map[string]string) {
	table := &termio.Table{}
	table.AppendHeader("Resource ID")
	table.AppendHeader("IP address")
	table.AppendHeader("Owner")
	table.AppendHeader("Unused since")
	if errors != nil {
		table.AppendHeader("Result")
	}
	for _, item := range items {
		table.Cell("%s", item.ResourceId)
		table.Cell("%s", item.IpAddress)
		table.Cell("%s", item.Owner)
		table.Cell("%s", item.UnusedSince.Format(time.RFC3339))
		if errors != nil {
			result := "reclaimed"
			if errors[item.ResourceId] != "" {
				result = "skipped: " + errors[item.ResourceId]
			}
			table.Cell("%s", result)
		}
	}
	table.Print()
}

func ipReclaimResultsTable(results []IpReclaimResult) {
	table := &termio.Table{}
	table.AppendHeader("Resource ID")
	table.AppendHeader("Result")
	for _, result := range results {
		outcome := "reclaimed"
		if result.Error != "" {
			outcome = "skipped: " + result.Error
		}
		table.Cell("%s", result.ResourceId)
		table.Cell("%s", outcome)
	}
	table.Print()
}

func PublicIpRetrieve(publicIpId string) (*orc.PublicIp, bool) {
	publicIps.Mu.Lock()
	job, ok := publicIps.Ips[publicIpId]
	publicIps.Mu.Unlock()

	if ok {
		return job, ok
	} else {
		request := orc.PublicIpsControlRetrieveRequest{Id: publicIpId}
		request.IncludeProduct = false
		request.IncludeUpdates = true
		ip, err := orc.PublicIpsControlRetrieve.Invoke(request)

		if err == nil {
			PublicIpTrackNew(ip)
			return &ip, true
		} else {
			return nil, false
		}
	}
}
