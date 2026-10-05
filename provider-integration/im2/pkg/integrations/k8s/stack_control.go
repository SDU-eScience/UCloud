package k8s

import (
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"fmt"
	"net/http"
	"strings"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const stackControlTokenPrefix = "sgr-"

const stackControlControlPlaneGroup = "control-plane"
const stackControlControlPlaneGroupLabel = "ucloud.dk/k8s-node-group"

type stackControlTokenRow struct {
	JobId          string
	StackInstance  string
	OwnerCreatedBy string
	OwnerProject   sql.Null[string]
	Provider       string
	TokenHash      []byte
}

func stackControlTokenHash(secret string) []byte {
	hashed := sha256.Sum256([]byte(secret))
	return hashed[:]
}

func stackControlTokenCreate(job *orc.Job) (string, bool) {
	secret := util.SecureToken()
	stackInstance := job.Specification.Labels[orc.ResourceLabelStackInstance]

	_, created := db.NewTx2(func(tx *db.Transaction) (struct{ JobId string }, bool) {
		return db.Get[struct{ JobId string }](
			tx,
			`
				insert into k8s.stack_grant_tokens(
					job_id, stack_instance, owner_created_by, owner_project, provider, token_hash
				)
				values (:job_id, :stack_instance, :owner_created_by, :owner_project, :provider, :token_hash)
				on conflict (job_id) do nothing
				returning job_id
			`,
			db.Params{
				"job_id":           job.Id,
				"stack_instance":   stackInstance,
				"owner_created_by": job.Owner.CreatedBy,
				"owner_project":    job.Owner.Project.Sql(),
				"provider":         job.Specification.Product.Provider,
				"token_hash":       stackControlTokenHash(secret),
			},
		)
	})

	if !created {
		return "", false
	}

	return stackControlTokenPrefix + job.Id + "-" + secret, true
}

func stackControlTokenParse(raw string) (string, string, bool) {
	payload, hasPrefix := strings.CutPrefix(raw, stackControlTokenPrefix)
	if !hasPrefix {
		return "", "", false
	}

	jobId, secret, hasSeparator := strings.Cut(payload, "-")
	if !hasSeparator || jobId == "" || secret == "" {
		return "", "", false
	}

	return jobId, secret, true
}

func stackControlForbidden() *util.HttpError {
	return util.HttpErr(http.StatusForbidden, "forbidden")
}

func stackControlControlPlaneJobValid(job *orc.Job) bool {
	if job == nil || strings.TrimSpace(job.Id) == "" || job.Status.State.IsFinal() {
		return false
	}
	if strings.TrimSpace(job.Owner.CreatedBy) == "" || strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance]) == "" {
		return false
	}
	if job.Specification.Product.Provider != cfg.Provider.Id {
		return false
	}
	return job.Specification.Labels[stackControlControlPlaneGroupLabel] == stackControlControlPlaneGroup
}

func stackControlOwnerScopeMatches(left orc.ResourceOwner, right orc.ResourceOwner) bool {
	leftUsername := strings.TrimSpace(left.CreatedBy)
	rightUsername := strings.TrimSpace(right.CreatedBy)
	if leftUsername == "" || rightUsername == "" || left.Project.Present != right.Project.Present {
		return false
	}

	if left.Project.Present {
		leftProject := strings.TrimSpace(left.Project.Value)
		rightProject := strings.TrimSpace(right.Project.Value)
		return leftProject != "" && leftProject == rightProject
	}

	return leftUsername == rightUsername
}

func stackControlTokenIssue(jobId string, stackInstance string, owner orc.ResourceOwner) (string, error) {
	jobId = strings.TrimSpace(jobId)
	stackInstance = strings.TrimSpace(stackInstance)
	if jobId == "" || stackInstance == "" {
		return "", fmt.Errorf("invalid control plane job")
	}

	job, ok := controller.JobRetrieve(jobId)
	if !ok || !stackControlControlPlaneJobValid(job) {
		return "", fmt.Errorf("invalid control plane job")
	}
	if strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance]) != stackInstance {
		return "", fmt.Errorf("control plane job belongs to a different stack")
	}
	if !stackControlOwnerScopeMatches(owner, job.Owner) {
		return "", fmt.Errorf("control plane job belongs to a different stack owner")
	}

	token, created := stackControlTokenCreate(job)
	if !created {
		return "", fmt.Errorf("a controller credential has already been issued for this job")
	}
	return token, nil
}

func stackControlJobMatches(job *orc.Job, row *stackControlTokenRow) bool {
	if !stackControlControlPlaneJobValid(job) {
		return false
	}

	if job.Owner.CreatedBy != row.OwnerCreatedBy {
		return false
	}

	if job.Owner.Project.Present != row.OwnerProject.Valid {
		return false
	}

	if row.OwnerProject.Valid && job.Owner.Project.Value != row.OwnerProject.V {
		return false
	}

	if job.Specification.Product.Provider != row.Provider {
		return false
	}

	if job.Specification.Labels[orc.ResourceLabelStackInstance] != row.StackInstance {
		return false
	}

	return true
}

func stackControlLiveControlPlaneJob(row *stackControlTokenRow) (*orc.Job, *util.HttpError) {
	if row.Provider != cfg.Provider.Id {
		return nil, stackControlForbidden()
	}

	job, ok := controller.JobRetrieve(row.JobId)
	if !ok || !stackControlJobMatches(job, row) {
		return nil, stackControlForbidden()
	}

	return job, nil
}

func stackControlTokenAuthenticate(rawToken string) (*orc.Job, *util.HttpError) {
	jobId, secret, ok := stackControlTokenParse(strings.TrimSpace(rawToken))
	if !ok {
		return nil, stackControlForbidden()
	}

	row, rowOk := db.NewTx2(func(tx *db.Transaction) (stackControlTokenRow, bool) {
		return db.Get[stackControlTokenRow](
			tx,
			`
				select job_id, stack_instance, owner_created_by, owner_project, provider, token_hash
				from k8s.stack_grant_tokens
				where job_id = :job_id
			`,
			db.Params{"job_id": jobId},
		)
	})

	if !rowOk {
		return nil, stackControlForbidden()
	}

	hashedSecret := stackControlTokenHash(secret)
	if subtle.ConstantTimeCompare(hashedSecret, row.TokenHash) != 1 {
		return nil, stackControlForbidden()
	}

	return stackControlLiveControlPlaneJob(&row)
}

func stackControlInitServer() {
	stackControlProxy(ucxapi.StackControlCreateIngress, orc.IngressesControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.IngressSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlCreatePublicIp, orc.PublicIpsControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.PublicIPSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlCreatePrivateNetwork, orc.PrivateNetworksControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.PrivateNetworkSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlCreatePrivateNetworkIp, orc.PrivateNetworkIpsControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.PrivateNetworkIpSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlCreateJob, orc.JobsControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.JobSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlCreateService, orc.ServicesControlCreate,
		func(job *orc.Job, r *orc.ControlCreateRequest[orc.ServiceSpecification]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlUpdateMembers, orc.ServicesControlUpdateMembers,
		func(job *orc.Job, r *orc.ServicesControlUpdateMembersRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlDeleteIngress, orc.IngressesControlDelete,
		func(job *orc.Job, r *orc.IngressesControlDeleteRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlBrowseIngresses, orc.IngressesControlBrowse,
		func(job *orc.Job, r *orc.IngressesControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlBrowseServices, orc.ServicesControlBrowse,
		func(job *orc.Job, r *orc.ServicesControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlBrowseJobs, orc.JobsControlBrowse,
		func(job *orc.Job, r *orc.JobsControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlBrowsePublicIps, orc.PublicIpsControlBrowse,
		func(job *orc.Job, r *orc.PublicIpsControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlBrowsePrivateNetworks, orc.PrivateNetworksControlBrowse,
		func(job *orc.Job, r *orc.PrivateNetworksControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlBrowsePrivateNetworkIps, orc.PrivateNetworkIpsControlBrowse,
		func(job *orc.Job, r *orc.PrivateNetworkIpsControlBrowseRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlRetrieveIngress, orc.IngressesControlRetrieve,
		func(job *orc.Job, r *orc.IngressesControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRetrieveService, orc.ServicesControlRetrieve,
		func(job *orc.Job, r *orc.ServicesControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRetrieveJob, orc.JobsControlRetrieve,
		func(job *orc.Job, r *orc.JobsControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRetrievePublicIp, orc.PublicIpsControlRetrieve,
		func(job *orc.Job, r *orc.PublicIpsControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRetrievePrivateNetwork, orc.PrivateNetworksControlRetrieve,
		func(job *orc.Job, r *orc.PrivateNetworksControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRetrievePrivateNetworkIp, orc.PrivateNetworkIpsControlRetrieve,
		func(job *orc.Job, r *orc.PrivateNetworkIpsControlRetrieveRequest) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlUpdateJobLabels, orc.JobsControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.JobsUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdateServiceLabels, orc.ServicesControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.ServicesUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdateIngressLabels, orc.IngressesControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.IngressesUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdatePublicIpLabels, orc.PublicIpsControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.PublicIpsUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdatePrivateNetworkLabels, orc.PrivateNetworksControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.PrivateNetworksUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdatePrivateNetworkIpLabels, orc.PrivateNetworkIpsControlUpdateLabels,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.PrivateNetworkIpsUpdateLabelsRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlTerminateJobs, orc.JobsControlTerminate,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlSuspendJobs, orc.JobsControlSuspend,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUnsuspendJobs, orc.JobsControlUnsuspend,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlExtendJobs, orc.JobsControlExtend,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.JobsExtendRequestItem]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlRenameJobs, orc.JobsControlRename,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.JobRenameRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlDeleteService, orc.ServicesControlDelete,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlDeletePublicIp, orc.PublicIpsControlDelete,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlDeletePrivateNetwork, orc.PrivateNetworksControlDelete,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlDeletePrivateNetworkIp, orc.PrivateNetworkIpsControlDelete,
		func(job *orc.Job, r *orc.ControlMutateRequest[fnd.FindByStringId]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProxy(ucxapi.StackControlUpdateServiceSpec, orc.ServicesControlUpdateSpec,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.ResourceUpdateAndId[orc.ServicesUpdateRequest]]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlSetIngressTarget, orc.IngressesControlSetTarget,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.IngressesSetTargetRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)
	stackControlProxy(ucxapi.StackControlUpdatePublicIpFirewall, orc.PublicIpsControlUpdateFirewall,
		func(job *orc.Job, r *orc.ControlMutateRequest[orc.PublicIpUpdateFirewallRequest]) *util.HttpError {
			r.JobId = job.Id
			return nil
		},
	)

	stackControlProducts(ucxapi.StackControlIngressProducts, shared.LinkSupport)
	stackControlProducts(ucxapi.StackControlServiceProducts, shared.ServiceSupport)
	stackControlProducts(ucxapi.StackControlJobProducts, shared.MachineSupport)
	stackControlProducts(ucxapi.StackControlPublicIpProducts, shared.IpSupport)
	stackControlProducts(ucxapi.StackControlPrivateNetworkProducts, shared.PrivateNetworkSupport)
	stackControlProducts(ucxapi.StackControlPrivateNetworkIpProducts, shared.PrivateNetworkIpSupport)
}

func stackControlProxy[CtrlReq any, Resp any](
	call rpc.Call[ucxapi.StackControlRequestOf[CtrlReq], Resp],
	control rpc.Call[CtrlReq, Resp],
	prepare func(job *orc.Job, request *CtrlReq) *util.HttpError,
) {
	call.Handler(func(info rpc.RequestInfo, request ucxapi.StackControlRequestOf[CtrlReq]) (Resp, *util.HttpError) {
		job, authErr := stackControlTokenAuthenticate(request.Token)
		if authErr != nil {
			var zero Resp
			return zero, authErr
		}

		if prepErr := prepare(job, &request.Request); prepErr != nil {
			var zero Resp
			return zero, prepErr
		}

		return control.Invoke(request.Request)
	})
}

func stackControlProducts[Resp any](
	call rpc.Call[ucxapi.StackControlProductsRequest, Resp],
	products Resp,
) {
	call.Handler(func(info rpc.RequestInfo, request ucxapi.StackControlProductsRequest) (Resp, *util.HttpError) {
		_, authErr := stackControlTokenAuthenticate(request.Token)
		if authErr != nil {
			var zero Resp
			return zero, authErr
		}
		return products, nil
	})
}
