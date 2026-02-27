package k8s

import (
	"os"
	"regexp"
	"slices"
	"strings"
	"sync/atomic"
	"time"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/tools/cache"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/containers"
	"ucloud.dk/pkg/integrations/k8s/kubevirt"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"

	ws "github.com/gorilla/websocket"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var containerCpu controller.JobsService
var virtCpu controller.JobsService

func InitCompute() controller.JobsService {
	containerCpu = containers.Init()
	virtCpu = kubevirt.Init()

	return controller.JobsService{
		Submit:                   submit,
		Terminate:                terminate,
		Extend:                   extend,
		RetrieveProducts:         retrieveProducts,
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        serverFindIngress,
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
		Suspend:                  suspend,
		Unsuspend:                unsuspend,
		HandleBuiltInVnc:         handleBuiltInVnc,
		PublicIPs: controller.PublicIPService{
			Create:           createPublicIp,
			Delete:           deletePublicIp,
			RetrieveProducts: retrievePublicIpProducts,
		},
		Ingresses: controller.IngressService{
			Create:           createIngress,
			Delete:           deleteIngress,
			RetrieveProducts: retrieveIngressProducts,
		},
		Licenses: controller.LicenseService{
			Create:           activateLicense,
			Delete:           deleteLicense,
			RetrieveProducts: retrieveLicenseProducts,
		},
	}
}

var nodes *shared.K8sResourceTracker[*corev1.Node]

var monitoringHealthCounter = atomic.Int64{}

func InitComputeLater() {
	controller.IAppReconfigureAll()

	initJobQueue()

	go func() {
		nodes = shared.NewResourceTracker[*corev1.Node](
			"",
			func(factory informers.SharedInformerFactory) cache.SharedIndexInformer {
				return factory.Core().V1().Nodes().Informer()
			},
			func(resource *corev1.Node) string {
				return resource.Name
			},
		)

		for util.IsAlive {
			loopMonitoring()
			monitoringHealthCounter.Add(1)
			time.Sleep(100 * time.Millisecond)
		}
	}()

	go monitoringHealthLoop()
}

func monitoringHealthLoop() {
	prevValue := int64(0)
	time.Sleep(2 * time.Minute) // Wait at least 2 minutes before starting health loop

	for util.IsAlive {
		current := monitoringHealthCounter.Load()
		if current == prevValue {
			log.Error("Monitoring loop hasn't been run at least once in the last 60 seconds. Monitoring is too slow. Deadlock?")
			os.Exit(1)
		}
		prevValue = current
		time.Sleep(60 * time.Second)
	}
}

func retrievePublicIpProducts() []orc.PublicIpSupport {
	return shared.IpSupport
}

func createPublicIp(ip *orc.PublicIp) *util.HttpError {
	result := controller.PublicIpAllocate(ip)
	accountPublicIps(ip.Owner)
	return result
}

func accountPublicIps(owner orc.ResourceOwner) {
	productName := shared.ServiceConfig.Compute.PublicIps.Name
	count := controller.PublicIpRetrieveUsedCount(owner)
	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project.Value),
				IsDeltaCharge: false,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     productName,
					Provider: cfg.Provider.Id,
				},
				Usage:       int64(count),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

func deletePublicIp(ip *orc.PublicIp) *util.HttpError {
	result := controller.PublicIpDelete(ip)
	accountPublicIps(ip.Owner)
	return result
}

func retrieveIngressProducts() []orc.IngressSupport {
	return shared.LinkSupport
}

var linkRegex = regexp.MustCompile("[a-z]([-_a-z0-9]){4,255}")

func createIngress(ingress *orc.Ingress) *util.HttpError {
	if ingress == nil {
		return util.ServerHttpError("Failed to create public link: ingress is nil")
	}

	owner := ingress.Owner.CreatedBy

	if len(ingress.Owner.Project.Value) > 0 {
		owner = ingress.Owner.Project.Value
	}

	domain := ingress.Specification.Domain
	prefix := shared.ServiceConfig.Compute.PublicLinks.Prefix
	suffix := shared.ServiceConfig.Compute.PublicLinks.Suffix

	isValid := strings.HasPrefix(domain, prefix) && strings.HasSuffix(domain, suffix)

	if !isValid {
		return util.UserHttpError("Specified domain is not valid.")
	}

	id, _ := strings.CutPrefix(domain, prefix)
	id, _ = strings.CutSuffix(id, suffix)

	if len(id) < 5 {
		return util.UserHttpError("Public links must be at least 5 characters long.")
	}

	if strings.HasSuffix(id, "-") || strings.HasSuffix(id, "_") {
		return util.UserHttpError("Public links cannot end with a dash or underscore.")
	}

	if !linkRegex.MatchString(id) {
		return util.UserHttpError("Public link must only contain letters a-z, numbers (0-9), dashes and underscores.")
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into ingresses(domain, owner)
				values (:domain, :owner) on conflict do nothing
			`,
			db.Params{
				"domain": domain,
				"owner":  owner,
			},
		)
	})

	status := util.Option[string]{}
	status.Set("Public link is ready for use")

	newUpdate := orc.IngressUpdate{
		State:     util.OptValue(orc.IngressStateReady),
		Timestamp: fnd.Timestamp(time.Now()),
		Status:    status,
	}

	_, err := orc.IngressesControlAddUpdate.Invoke(fnd.BulkRequestOf(orc.ResourceUpdateAndId[orc.IngressUpdate]{
		Id:     ingress.Id,
		Update: newUpdate,
	}))

	if err == nil {
		ingress.Updates = append(ingress.Updates, newUpdate)
		controller.LinkTrack(*ingress)
		accountPublicLinks(ingress.Owner)
		return nil
	} else {
		_ = deleteIngress(ingress)
		log.Warn("Failed to create public link due to an error between UCloud and the provider: %s", err)
		return err
	}
}

func deleteIngress(ingress *orc.Ingress) *util.HttpError {
	result := controller.LinkDeleteTracked(ingress, func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from ingresses
				where domain = :domain
			`,
			db.Params{
				"domain": ingress.Specification.Domain,
			},
		)
	})

	accountPublicLinks(ingress.Owner)
	return result
}

func accountPublicLinks(owner orc.ResourceOwner) {
	productName := shared.ServiceConfig.Compute.PublicLinks.Name
	count := controller.LinkRetrieveUsedCount(owner)
	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project.Value),
				IsDeltaCharge: false,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     productName,
					Provider: cfg.Provider.Id,
				},
				Usage:       int64(count),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

func retrieveLicenseProducts() []orc.LicenseSupport {
	return controller.LicenseFetchSupport()
}

func activateLicense(license *orc.License) *util.HttpError {
	result := controller.LicenseActivate(license)
	accountLicenses(license.Specification.Product.Category, license.Owner)
	return result
}

func deleteLicense(license *orc.License) *util.HttpError {
	result := controller.LicenseDelete(license)
	accountLicenses(license.Specification.Product.Category, license.Owner)
	return result
}

func accountLicenses(licenseName string, owner orc.ResourceOwner) {
	count := controller.LicenseRetrieveUsedCount(licenseName, owner)
	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project.Value),
				IsDeltaCharge: false,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     licenseName,
					Provider: cfg.Provider.Id,
				},
				Usage:       int64(count),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

func retrieveProducts() []orc.JobSupport {
	support := slices.Clone(shared.MachineSupport)

	statusPtr := schedulerStatus.Load()
	if statusPtr != nil {
		status := *statusPtr
		if status != nil {
			for i := 0; i < len(support); i++ {
				elem := &support[i]
				elemStatus, ok := status[elem.Product]
				if ok && elemStatus.Present {
					elem.QueueStatus = elemStatus
				}
			}
		}
	}

	return support
}

func backendByAppIsKubevirt(app *orc.Application) bool {
	return backendByApp(app) == &virtCpu
}

func backendByAppIsContainers(app *orc.Application) bool {
	return backendByApp(app) == &containerCpu
}

func backendByApp(app *orc.Application) *controller.JobsService {
	tool := &app.Invocation.Tool.Tool.Value.Description
	switch tool.Backend {
	case orc.ToolBackendDocker:
		fallthrough
	case orc.ToolBackendSingularity:
		fallthrough
	case orc.ToolBackendNative:
		return &containerCpu
	case orc.ToolBackendVirtualMachine:
		return &virtCpu
	default:
		return &containerCpu
	}
}

func backendIsKubevirt(job *orc.Job) bool {
	return backend(job) == &virtCpu
}

func backendIsContainers(job *orc.Job) bool {
	return backend(job) == &containerCpu
}

func backend(job *orc.Job) *controller.JobsService {
	app := &job.Status.ResolvedApplication.Value
	return backendByApp(app)
}

func submit(job orc.Job) (util.Option[string], *util.HttpError) {
	_, isIApp := controller.IntegratedApplications[job.Specification.Product.Category]
	if isIApp {
		return util.OptNone[string](), util.UserHttpError("This product does not allow job-submissions")
	}

	if reason := IsJobLocked(&job); reason.Present {
		return util.OptNone[string](), reason.Value.Err
	}

	if backendIsKubevirt(&job) && shared.IsSensitiveProject(job.Owner.Project.Value) {
		// NOTE(Dan): Feel free to remove this once VMs have been prepared for sensitive projects.
		return util.OptNone[string](), util.UserHttpError("This project is not allowed to use virtual machines")
	}

	shared.RequestSchedule(&job)
	controller.JobTrackNew(job)
	return util.OptNone[string](), nil
}

func terminate(request controller.JobTerminateRequest) *util.HttpError {
	return backend(request.Job).Terminate(request)
}

func extend(request orc.JobsProviderExtendRequestItem) *util.HttpError {
	return backend(&request.Job).Extend(request)
}

func follow(session *controller.FollowJobSession) {
	backend(session.Job).Follow(session)
}

func handleShell(session *controller.ShellSession, cols int, rows int) {
	if session.Folder != "" {
		containerCpu.HandleShell(session, cols, rows)
	} else {
		backend(session.Job).HandleShell(session, cols, rows)
	}
}

func serverFindIngress(job *orc.Job, rank int, suffix util.Option[string]) []controller.ConfiguredWebIngress {
	return backend(job).ServerFindIngress(job, rank, suffix)
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (controller.ConfiguredWebSession, *util.HttpError) {
	return backend(job).OpenWebSession(job, rank, target)
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	fn := backendByApp(app).RequestDynamicParameters
	if fn == nil {
		return nil
	} else {
		return fn(owner, app)
	}
}

func unsuspend(job orc.Job) *util.HttpError {
	fn := backend(&job).Unsuspend
	if fn != nil {
		return fn(job)
	}
	return nil
}

func suspend(job orc.Job) *util.HttpError {
	fn := backend(&job).Suspend
	if fn != nil {
		return fn(job)
	}
	return nil
}

func handleBuiltInVnc(job *orc.Job, rank int, conn *ws.Conn) {
	fn := backend(job).HandleBuiltInVnc
	if fn != nil {
		fn(job, rank, conn)
	}
}
