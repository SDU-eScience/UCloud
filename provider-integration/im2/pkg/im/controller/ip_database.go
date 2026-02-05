package controller

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"ucloud.dk/pkg/cli"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
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

var (
	ipcRetrieveIpPool = ipc.NewCall[util.Empty, []IpPoolEntry]("publicIps.retrievePool")
	ipcAddToPool      = ipc.NewCall[ipcAddToPoolRequest, util.Empty]("publicIps.addToPool")
	ipcRemoveFromPool = ipc.NewCall[string, util.Empty]("publicIps.removeFromPool")
)

// initIpDatabase is invoked by the job_database
func initIpDatabase() {
	if !RunsServerCode() {
		return
	}

	publicIps.Mu.Lock()
	publicIps.Ips = make(map[string]*orc.PublicIp)
	publicIps.ExternalAddressesInUse = make(map[string]string)
	fetchAllPublicIps()
	publicIps.Mu.Unlock()

	loadPool()

	ipcAddToPool.Handler(func(r *ipc.Request[ipcAddToPoolRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		err := AddToIpPool(r.Payload.Public, r.Payload.Private)
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

func loadPool() {
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
				ip, ok := RetrieveIp(id)
				if ok {
					deleteFn := Jobs.PublicIPs.Delete
					if deleteFn != nil {
						err := deleteFn(ip)
						if err != nil {
							log.Info("Could not delete IP %s: %s", id, err)
						}
					} else {
						err := DeleteIpAddress(ip)
						log.Info("Could not delete IP %s: %s", id, err)
					}
				} else {
					log.Info("Could not delete IP %s", id)
				}
			}
		}()
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

func RetrievePublicIp(id string) (*orc.PublicIp, bool) {
	publicIps.Mu.Lock()
	publicIp, ok := publicIps.Ips[id]
	publicIps.Mu.Unlock()

	if ok {
		return publicIp, ok
	} else {
		request := orc.PublicIpsControlRetrieveRequest{Id: id}
		request.IncludeProduct = true
		request.IncludeUpdates = true
		publicIp, err := orc.PublicIpsControlRetrieve.Invoke(request)
		if err == nil {
			TrackNewPublicIp(publicIp)
			return &publicIp, true
		} else {
			return nil, false
		}
	}
}

type IpPoolEntry struct {
	Subnet        string
	PrivateSubnet string
	Allocated     int
	Remaining     int
}

func RetrieveIpPool() []IpPoolEntry {
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

func AllocateIpAddress(target *orc.PublicIp) *util.HttpError {
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
		_ = DeleteIpAddress(target)
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
			TrackNewPublicIp(*target)
			return nil
		} else {
			log.Info("Failed to allocate an IP address due to an error between UCloud and the provider: %s", err)

			_ = DeleteIpAddress(target)
			return err
		}
	}
}

func DeleteIpAddress(address *orc.PublicIp) *util.HttpError {
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

func RetrieveUsedIpAddressCount(owner orc.ResourceOwner) int {
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

func AddToIpPool(subnet string, privateSubnet string) error {
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

func RemoveFromIpPool(subnet string) error {
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

func BindIpsToJob(job *orc.Job) ([]orc.PublicIp, []net.IP, error) {
	publicIps.Mu.Lock()

	var result []orc.PublicIp
	var privateIps []net.IP

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

	var err error

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
				boundtoJob, ok := RetrieveJob(jobId)
				if ok && !boundtoJob.Status.State.IsFinal() {
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

	publicIps.Mu.Unlock() // Need to unlock before TrackNewPublicIp

	if err != nil {
		return nil, nil, err
	} else {
		for _, ip := range result {
			TrackNewPublicIp(ip)
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

func IpCanUse(ip net.IP) bool {
	ip = ip.To4()
	if ip == nil {
		return true
	}
	v := binary.BigEndian.Uint32(ip)
	last16 := v & 0xFFFF
	return last16 != 0x0001 && last16 != 0x00FF
}

func UnbindIpsFromJob(job *orc.Job) {
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

	publicIps.Mu.Unlock() // Need to unlock before TrackNewPublicIp

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

func RetrieveIp(publicIpId string) (*orc.PublicIp, bool) {
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
			TrackNewPublicIp(ip)
			return &ip, true
		} else {
			return nil, false
		}
	}
}
