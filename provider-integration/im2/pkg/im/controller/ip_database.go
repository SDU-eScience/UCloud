package controller

import (
	"encoding/json"
	"fmt"
	"math/big"
	"net"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
	"ucloud.dk/pkg/cli"
	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

// This file contains an in-memory version of all public IPs. It is similar to the job_database.go file. This file is
// only capable of operating in server mode with no user-mode functionality. This will be changed if and when we need
// it. The file is initialized by the job_database and often controlled by the job_database in response to job events.
// This file also exposes several IPC calls and CLI stub to manage a pool of available (external) IP addresses.

var publicIps = map[string]*orc.PublicIp{}
var ipPool []net.IPNet
var externalAddressesInUse = map[string]string{} // Textual IP (net.IP) to orc.PublicIp identifier

var publicIpsMutex = sync.Mutex{}

var (
	ipcRetrieveIpPool = ipc.NewCall[util.Empty, []IpPoolEntry]("publicIps.retrievePool")
	ipcAddToPool      = ipc.NewCall[string, util.Empty]("publicIps.addToPool")
	ipcRemoveFromPool = ipc.NewCall[string, util.Empty]("publicIps.removeFromPool")
)

// initIpDatabase is invoked by the job_database
func initIpDatabase() {
	if !RunsServerCode() {
		return
	}

	publicIpsMutex.Lock()
	defer publicIpsMutex.Unlock()
	fetchAllPublicIps()
	loadPool()

	ipcAddToPool.Handler(func(r *ipc.Request[string]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		err := AddToIpPool(r.Payload)
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

		err := RemoveFromIpPool(r.Payload)
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

		pool := RetrieveIpPool()
		return ipc.Response[[]IpPoolEntry]{
			StatusCode: http.StatusOK,
			Payload:    pool,
		}
	})
}

func fetchAllPublicIps() {
	next := ""

	for {
		page, err := orc.BrowsePublicIps(next, orc.BrowseIpsFlags{
			IncludeProduct: false,
			IncludeUpdates: true,
		})

		if err != nil {
			log.Warn("Failed to fetch jobs: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			ip := &page.Items[i]
			publicIps[ip.Id] = ip
			if ip.Status.IpAddress.Present {
				externalAddressesInUse[ip.Status.IpAddress.Value] = ip.Id
			}
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}

func loadPool() {
	rows := db.NewTx(func(tx *db.Transaction) []struct{ Subnet string } {
		return db.Select[struct{ Subnet string }](
			tx,
			`
				select subnet
				from ip_pool
		    `,
			db.Params{},
		)
	})

	for _, row := range rows {
		_, parsedNet, err := net.ParseCIDR(row.Subnet)
		if err != nil {
			log.Warn("Could not load subnet '%s': %s", row.Subnet, err)
			continue
		}

		if parsedNet == nil {
			log.Warn("Could not load subnet '%s'", row.Subnet)
			continue
		}

		ipPool = append(ipPool, *parsedNet)
	}
}

func TrackNewPublicIp(ip orc.PublicIp) {
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

	publicIpsMutex.Lock()
	{
		existingIp, ok := publicIps[ip.Id]
		publicIps[ip.Id] = &ip

		if ok {
			existingIpAddress := existingIp.Status.IpAddress
			if existingIpAddress.Present {
				delete(externalAddressesInUse, existingIpAddress.Value)
			}
		}

		if ip.Status.IpAddress.Present {
			externalAddressesInUse[ip.Status.IpAddress.Value] = ip.Id
		}
	}
	publicIpsMutex.Unlock()

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
				"project_id":       ip.Owner.Project,
				"product_id":       ip.Specification.Product.Id,
				"product_category": ip.Specification.Product.Category,
				"resource":         string(jsonified),
			},
		)
	})
}

func RetrievePublicIp(id string) (*orc.PublicIp, bool) {
	publicIpsMutex.Lock()
	publicIp, ok := publicIps[id]
	publicIpsMutex.Unlock()

	if ok {
		return publicIp, ok
	} else {
		publicIp, err := orc.RetrievePublicIp(id, orc.BrowseIpsFlags{IncludeProduct: true, IncludeUpdates: true})
		if err == nil {
			TrackNewPublicIp(publicIp)
			return &publicIp, true
		} else {
			return nil, false
		}
	}
}

type IpPoolEntry struct {
	Subnet    string
	Allocated int
	Remaining int
}

func RetrieveIpPool() []IpPoolEntry {
	publicIpsMutex.Lock()
	defer publicIpsMutex.Unlock()

	var result []IpPoolEntry

	for _, subnet := range ipPool {
		included, maskSize := subnet.Mask.Size()
		ipsInSubnet := 1 << int64(maskSize-included)
		numAllocated := 0

		for allocated, _ := range externalAddressesInUse {
			parsed := net.ParseIP(allocated)
			if parsed != nil && subnet.Contains(parsed) {
				numAllocated++
			}
		}

		result = append(result, IpPoolEntry{
			Subnet:    subnet.String(),
			Allocated: numAllocated,
			Remaining: ipsInSubnet - numAllocated,
		})
	}

	return result
}

func AllocateIpAddress(target *orc.PublicIp) error {
	if target == nil {
		return fmt.Errorf("target is nil")
	}

	publicIpsMutex.Lock()

	allocatedIp := util.Option[string]{}

	// NOTE(Dan): Performance of this code heavily degrades as we get a lot of IP addresses. This is not a problem we
	// have today. In case we ever get a large block of IPs, someone should update the code to actually be smart
	// about allocation.
outer:
	for _, subnet := range ipPool {
		included, maskSize := subnet.Mask.Size()
		ipsInSubnet := 1 << int64(maskSize-included)

		numericIp := &big.Int{}
		numericIp.SetBytes(subnet.IP)

		for i := 0; i < ipsInSubnet; i++ {
			toAdd := big.NewInt(int64(i))

			newNumericIp := &big.Int{}
			newNumericIp.Add(numericIp, toAdd)

			newIp := net.IP(newNumericIp.Bytes())
			if len(newIp) == net.IPv4len && (newIp[3] == 0 || newIp[3] == 1 || newIp[3] == 255) {
				continue
			}

			newIpString := newIp.String()
			_, exists := externalAddressesInUse[newIpString]
			if !exists {
				allocatedIp.Set(newIpString)
				externalAddressesInUse[newIpString] = target.Id
				break outer
			}
		}
	}

	publicIpsMutex.Unlock()

	if !allocatedIp.Present {
		return util.HttpErr(http.StatusBadRequest, "%s has no more IP addresses available", cfg.Provider.Id)
	} else {
		newUpdate := orc.PublicIpUpdate{
			State:           util.OptValue(orc.PublicIpStateReady),
			ChangeIpAddress: util.OptValue(true),
			NewIpAddress:    util.OptValue(allocatedIp.Value),
			Timestamp:       fnd.Timestamp(time.Now()),
		}

		err := orc.UpdatePublicIps(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.PublicIpUpdate]]{
			Items: []orc.ResourceUpdateAndId[orc.PublicIpUpdate]{
				{
					Id:     target.Id,
					Update: newUpdate,
				},
			},
		})

		if err == nil {
			target.Updates = append(target.Updates, newUpdate)
			TrackNewPublicIp(*target)
			return nil
		} else {
			log.Info("Failed to allocate an IP address due to an error between UCloud and the provider: %s", err)

			publicIpsMutex.Lock()
			delete(externalAddressesInUse, allocatedIp.Value)
			publicIpsMutex.Unlock()
			return err
		}
	}
}

func DeleteIpAddress(address *orc.PublicIp) error {
	if len(address.Status.BoundTo) > 0 {
		return util.UserHttpError("This IP is currently in use by job: %v", strings.Join(address.Status.BoundTo, ", "))
	}

	publicIpsMutex.Lock()

	if address.Status.IpAddress.Present {
		delete(externalAddressesInUse, address.Status.IpAddress.Value)
	}

	delete(publicIps, address.Id)

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

	publicIpsMutex.Unlock()
	return nil
}

func AddToIpPool(subnet string) error {
	ip, parsedSubnet, err := net.ParseCIDR(subnet)
	if err != nil || parsedSubnet == nil {
		return fmt.Errorf("invalid subnet specified: %s", err)
	}

	if _, maskSize := parsedSubnet.Mask.Size(); maskSize == 0 {
		// I don't think ParseCIDR will do this, but just in case.
		return fmt.Errorf("invalid subnet specified: non-canonical subnet specified")
	}

	publicIpsMutex.Lock()

	for _, existingSubnet := range ipPool {
		if existingSubnet.Contains(ip) || parsedSubnet.Contains(existingSubnet.IP) {
			err = fmt.Errorf("subnet overlaps with existing subnet (%s is in %s)", parsedSubnet.String(), existingSubnet.String())
			break
		}
	}

	if err == nil {
		ipPool = append(ipPool, *parsedSubnet)
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into ip_pool(subnet)
					values (:subnet) on conflict do nothing 
			    `,
				db.Params{
					"subnet": subnet,
				},
			)
		})
	}

	publicIpsMutex.Unlock()

	return err
}

func RemoveFromIpPool(subnet string) error {
	_, parsedSubnet, err := net.ParseCIDR(subnet)
	if err != nil || parsedSubnet == nil {
		return fmt.Errorf("invalid subnet specified: %s", err)
	}
	parsedString := parsedSubnet.String()

	publicIpsMutex.Lock()
	defer publicIpsMutex.Unlock()

	subnetIdx := -1
	for i, existingSubnet := range ipPool {
		if existingSubnet.String() == subnet || existingSubnet.String() == parsedString {
			subnetIdx = i
			break
		}
	}

	if subnetIdx == -1 {
		return fmt.Errorf("subnet not in pool (partial removes are not possible)")
	}

	var toInvalidate []string
	var runningJobs []string

	for _, ip := range publicIps {
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

	slices.Delete(ipPool, subnetIdx, subnetIdx+1)

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

func BindIpsToJob(job *orc.Job) ([]orc.PublicIp, error) {
	publicIpsMutex.Lock()

	var result []orc.PublicIp

	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps[v.Id]
			if ok {
				result = append(result, *ip)
			}
		}
	}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps[v.Id]
			if ok {
				result = append(result, *ip)
			}
		}
	}

	var err error

	for i := 0; i < len(result); i++ {
		ip := &result[i]

		if !ip.Status.IpAddress.Present {
			err = util.ServerHttpError(
				"IP %v is not ready yet - Try recreating it",
				ip.Id,
			)
			break
		}

		if len(ip.Status.BoundTo) > 0 {
			for _, jobId := range ip.Status.BoundTo {
				boundtoJob, ok := RetrieveJob(jobId)
				if ok && !boundtoJob.Status.State.IsFinal() {
					err = util.UserHttpError(
						"%s (%v) is already bound to job %s",
						ip.Status.IpAddress.Value,
						ip.Id,
						strings.Join(ip.Status.BoundTo, ", "),
					)
					break
				}
			}

			if err != nil {
				break
			}
		}

		ip.Status.BoundTo = []string{job.Id}
	}

	publicIpsMutex.Unlock() // Need to unlock before TrackNewPublicIp

	if err != nil {
		return nil, err
	} else {
		for _, ip := range result {
			TrackNewPublicIp(ip)
		}

		return result, nil
	}
}

func UnbindIpsFromJob(job *orc.Job) {
	publicIpsMutex.Lock()

	var result []orc.PublicIp
	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps[v.Id]
			if ok {
				result = append(result, *ip)
			}
		}
	}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeNetwork {
			ip, ok := publicIps[v.Id]
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

	publicIpsMutex.Unlock() // Need to unlock before TrackNewPublicIp

	for _, ip := range result {
		TrackNewPublicIp(ip)
	}
}

func IpPoolCliStub(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
		return
	}

	switch {
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
			_, err = ipcAddToPool.Invoke(subnet.Value)
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

func RetrieveIp(publicIpId string) (*orc.PublicIp, bool) {
	publicIpsMutex.Lock()
	job, ok := publicIps[publicIpId]
	publicIpsMutex.Unlock()

	if ok {
		return job, ok
	} else {
		ip, err := orc.RetrievePublicIp(publicIpId, orc.BrowseIpsFlags{
			IncludeProduct: false,
			IncludeUpdates: true,
		})

		if err == nil {
			TrackNewPublicIp(ip)
			return &ip, true
		} else {
			return nil, false
		}
	}
}
