package k8s

import (
	"regexp"
	"strings"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"

	ws "github.com/gorilla/websocket"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/kubevirt"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var containerCpu ctrl.JobsService
var virtCpu ctrl.JobsService

func InitCompute() ctrl.JobsService {
	containerCpu = containers.Init()
	virtCpu = kubevirt.Init()

	return ctrl.JobsService{
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
		PublicIPs: ctrl.PublicIPService{
			Create:           createPublicIp,
			Delete:           deletePublicIp,
			RetrieveProducts: retrievePublicIpProducts,
		},
		Ingresses: ctrl.IngressService{
			Create:           createIngress,
			Delete:           deleteIngress,
			RetrieveProducts: retrieveIngressProducts,
		},
		Licenses: ctrl.LicenseService{
			Create:           activateLicense,
			Delete:           deleteLicense,
			RetrieveProducts: retrieveLicenseProducts,
		},
	}
}

func InitComputeLater() {
	ctrl.ReconfigureAllIApps()

	go func() {
		for util.IsAlive {
			// TODO
			// TODO Need to resubmit old in-queue entries to the queue
			// TODO

			loopMonitoring()
			time.Sleep(100 * time.Millisecond)
		}
	}()
}

func retrievePublicIpProducts() []orc.PublicIpSupport {
	return shared.IpSupport
}

func createPublicIp(ip *orc.PublicIp) error {
	result := ctrl.AllocateIpAddress(ip)
	accountPublicIps(ip.Owner)
	return result
}

func accountPublicIps(owner orc.ResourceOwner) {
	productName := shared.ServiceConfig.Compute.PublicIps.Name
	count := ctrl.RetrieveUsedIpAddressCount(owner)
	_, _ = apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{
		Items: []apm.UsageReportItem{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project),
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

func deletePublicIp(ip *orc.PublicIp) error {
	result := ctrl.DeleteIpAddress(ip)
	accountPublicIps(ip.Owner)
	return result
}

func retrieveIngressProducts() []orc.IngressSupport {
	return shared.LinkSupport
}

var linkRegex = regexp.MustCompile("[a-z]([-_a-z0-9]){4,255}")

func createIngress(ingress *orc.Ingress) error {
	if ingress == nil {
		return util.ServerHttpError("Failed to create public link: ingress is nil")
	}

	owner := ingress.Owner.CreatedBy

	if len(ingress.Owner.Project) > 0 {
		owner = ingress.Owner.Project
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

	err := orc.UpdateIngresses(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.IngressUpdate]]{
		Items: []orc.ResourceUpdateAndId[orc.IngressUpdate]{
			{
				Id:     ingress.Id,
				Update: newUpdate,
			},
		},
	})

	if err == nil {
		ingress.Updates = append(ingress.Updates, newUpdate)
		ctrl.TrackLink(*ingress)
		accountPublicLinks(ingress.Owner)
		return nil
	} else {
		_ = deleteIngress(ingress)
		log.Warn("Failed to create public link due to an error between UCloud and the provider: %s", err)
		return err
	}
}

func deleteIngress(ingress *orc.Ingress) error {
	result := ctrl.DeleteTrackedLink(ingress, func(tx *db.Transaction) {
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
	count := ctrl.RetrieveUsedLinkCount(owner)
	_, _ = apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{
		Items: []apm.UsageReportItem{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project),
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
	return ctrl.FetchLicenseSupport()
}

func activateLicense(license *orc.License) error {
	result := ctrl.ActivateLicense(license)
	accountLicenses(license.Specification.Product.Category, license.Owner)
	return result
}

func deleteLicense(license *orc.License) error {
	result := ctrl.DeleteLicense(license)
	accountLicenses(license.Specification.Product.Category, license.Owner)
	return result
}

func accountLicenses(licenseName string, owner orc.ResourceOwner) {
	count := ctrl.RetrieveUsedLicenseCount(licenseName, owner)
	_, _ = apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{
		Items: []apm.UsageReportItem{
			{
				Owner:         apm.WalletOwnerFromIds(owner.CreatedBy, owner.Project),
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
	return shared.MachineSupport
}

func backendByAppIsKubevirt(app *orc.Application) bool {
	return backendByApp(app) == &virtCpu
}

func backendByAppIsContainers(app *orc.Application) bool {
	return backendByApp(app) == &containerCpu
}

func backendByApp(app *orc.Application) *ctrl.JobsService {
	tool := &app.Invocation.Tool.Tool.Description
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

func backend(job *orc.Job) *ctrl.JobsService {
	app := &job.Status.ResolvedApplication
	return backendByApp(app)
}

func submit(request ctrl.JobSubmitRequest) (util.Option[string], error) {
	_, isIApp := ctrl.IntegratedApplications[request.JobToSubmit.Specification.Product.Category]
	if isIApp {
		return util.OptNone[string](), util.UserHttpError("This product does not allow job-submissions")
	}

	if reason := IsJobLocked(request.JobToSubmit); reason.Present {
		return util.OptNone[string](), reason.Value.Err
	}

	if backendIsKubevirt(request.JobToSubmit) && shared.IsSensitiveProject(request.JobToSubmit.Owner.Project) {
		// NOTE(Dan): Feel free to remove this once VMs have been prepared for sensitive projects.
		return util.OptNone[string](), util.UserHttpError("This project is not allowed to use virtual machines")
	}

	shared.RequestSchedule(request.JobToSubmit)
	ctrl.TrackNewJob(*request.JobToSubmit)
	return util.OptNone[string](), nil
}

func terminate(request ctrl.JobTerminateRequest) error {
	return backend(request.Job).Terminate(request)
}

func extend(request ctrl.JobExtendRequest) error {
	return backend(request.Job).Extend(request)
}

func follow(session *ctrl.FollowJobSession) {
	backend(session.Job).Follow(session)
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
	if session.Folder != "" {
		containerCpu.HandleShell(session, cols, rows)
	} else {
		backend(session.Job).HandleShell(session, cols, rows)
	}
}

func serverFindIngress(job *orc.Job, rank int, suffix util.Option[string]) ctrl.ConfiguredWebIngress {
	return backend(job).ServerFindIngress(job, rank, suffix)
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
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

func unsuspend(request ctrl.JobUnsuspendRequest) error {
	fn := backend(request.Job).Unsuspend
	if fn != nil {
		return fn(request)
	}
	return nil
}

func suspend(request ctrl.JobSuspendRequest) error {
	fn := backend(request.Job).Suspend
	if fn != nil {
		return fn(request)
	}
	return nil
}

func handleBuiltInVnc(job *orc.Job, rank int, conn *ws.Conn) {
	fn := backend(job).HandleBuiltInVnc
	if fn != nil {
		fn(job, rank, conn)
	}
}
