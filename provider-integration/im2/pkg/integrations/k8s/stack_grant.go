package k8s

import (
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"fmt"
	"net/http"
	"slices"
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

const stackGrantTokenPrefix = "sgr-"

const stackGrantControlPlaneGroup = "control-plane"
const stackGrantControlPlaneGroupLabel = "ucloud.dk/k8s-node-group"

type stackGrantTokenRow struct {
	JobId          string
	StackInstance  string
	OwnerCreatedBy string
	OwnerProject   sql.Null[string]
	Provider       string
	TokenHash      []byte
}

func stackGrantTokenHash(secret string) []byte {
	hashed := sha256.Sum256([]byte(secret))
	return hashed[:]
}

func stackGrantTokenCreate(job *orc.Job) (string, bool) {
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
				"token_hash":       stackGrantTokenHash(secret),
			},
		)
	})

	if !created {
		return "", false
	}

	return stackGrantTokenPrefix + job.Id + "-" + secret, true
}

func stackGrantTokenParse(raw string) (string, string, bool) {
	payload, hasPrefix := strings.CutPrefix(raw, stackGrantTokenPrefix)
	if !hasPrefix {
		return "", "", false
	}

	jobId, secret, hasSeparator := strings.Cut(payload, "-")
	if !hasSeparator || jobId == "" || secret == "" {
		return "", "", false
	}

	return jobId, secret, true
}

func stackGrantTokenForbidden() *util.HttpError {
	return util.HttpErr(http.StatusForbidden, "forbidden")
}

func stackGrantControlPlaneJobValid(job *orc.Job) bool {
	if job == nil || strings.TrimSpace(job.Id) == "" || job.Status.State.IsFinal() {
		return false
	}
	if strings.TrimSpace(job.Owner.CreatedBy) == "" || strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance]) == "" {
		return false
	}
	if job.Specification.Product.Provider != cfg.Provider.Id {
		return false
	}
	return job.Specification.Labels[stackGrantControlPlaneGroupLabel] == stackGrantControlPlaneGroup
}

func stackGrantOwnerScopeMatches(left orc.ResourceOwner, right orc.ResourceOwner) bool {
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

func stackGrantTokenIssue(jobId string, stackInstance string, owner orc.ResourceOwner) (string, error) {
	jobId = strings.TrimSpace(jobId)
	stackInstance = strings.TrimSpace(stackInstance)
	if jobId == "" || stackInstance == "" {
		return "", fmt.Errorf("invalid control plane job")
	}

	job, ok := controller.JobRetrieve(jobId)
	if !ok || !stackGrantControlPlaneJobValid(job) {
		return "", fmt.Errorf("invalid control plane job")
	}
	if strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance]) != stackInstance {
		return "", fmt.Errorf("control plane job belongs to a different stack")
	}
	if !stackGrantOwnerScopeMatches(owner, job.Owner) {
		return "", fmt.Errorf("control plane job belongs to a different stack owner")
	}

	token, created := stackGrantTokenCreate(job)
	if !created {
		return "", fmt.Errorf("a controller credential has already been issued for this job")
	}
	return token, nil
}

func stackGrantJobMatches(job *orc.Job, row *stackGrantTokenRow) bool {
	if !stackGrantControlPlaneJobValid(job) {
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

func stackGrantLiveControlPlaneJob(row *stackGrantTokenRow) (*orc.Job, *util.HttpError) {
	if row.Provider != cfg.Provider.Id {
		return nil, stackGrantTokenForbidden()
	}

	job, ok := controller.JobRetrieve(row.JobId)
	if !ok || !stackGrantJobMatches(job, row) {
		return nil, stackGrantTokenForbidden()
	}

	return job, nil
}

func stackGrantTokenAuthenticate(rawToken string) (*orc.Job, *util.HttpError) {
	jobId, secret, ok := stackGrantTokenParse(strings.TrimSpace(rawToken))
	if !ok {
		return nil, stackGrantTokenForbidden()
	}

	row, rowOk := db.NewTx2(func(tx *db.Transaction) (stackGrantTokenRow, bool) {
		return db.Get[stackGrantTokenRow](
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
		return nil, stackGrantTokenForbidden()
	}

	hashedSecret := stackGrantTokenHash(secret)
	if subtle.ConstantTimeCompare(hashedSecret, row.TokenHash) != 1 {
		return nil, stackGrantTokenForbidden()
	}

	return stackGrantLiveControlPlaneJob(&row)
}

func stackGrantLabels(job *orc.Job) map[string]string {
	labels := map[string]string{
		orc.ResourceLabelStackInstance: job.Specification.Labels[orc.ResourceLabelStackInstance],
	}

	if value := strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStack]); value != "" {
		labels[orc.ResourceLabelStack] = value
	}

	if value := strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackName]); value != "" {
		labels[orc.ResourceLabelStackName] = value
	}

	return labels
}

func stackGrantInitServer() {
	stackGrantCreateServer(ucxapi.StackGrantCreateIngress, orc.IngressesControlCreate,
		func(s *orc.IngressSpecification, labels map[string]string) {
			s.Labels = util.MapMerge(s.Labels, labels)
		},
	)
	stackGrantCreateServer(ucxapi.StackGrantCreatePublicIp, orc.PublicIpsControlCreate,
		func(s *orc.PublicIPSpecification, labels map[string]string) {
			s.Labels = util.MapMerge(s.Labels, labels)
		},
	)
	stackGrantCreateServer(ucxapi.StackGrantCreatePrivateNetwork, orc.PrivateNetworksControlCreate,
		func(s *orc.PrivateNetworkSpecification, labels map[string]string) {
			s.Labels = util.MapMerge(s.Labels, labels)
		},
	)
	stackGrantCreateServer(ucxapi.StackGrantCreatePrivateNetworkIp, orc.PrivateNetworkIpsControlCreate,
		func(s *orc.PrivateNetworkIpSpecification, labels map[string]string) {
			s.Labels = util.MapMerge(s.Labels, labels)
		},
	)
	stackGrantCreateServer(ucxapi.StackGrantCreateJob, orc.JobsControlCreate,
		func(s *orc.JobSpecification, labels map[string]string) { s.Labels = util.MapMerge(s.Labels, labels) },
	)
	stackGrantCreateServer(ucxapi.StackGrantCreateService, orc.ServicesControlCreate,
		func(s *orc.ServiceSpecification, labels map[string]string) {
			s.Labels = util.MapMerge(s.Labels, labels)
		},
	)

	ucxapi.StackGrantBrowseIngresses.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantBrowseIngressesRequest) ([]orc.Ingress, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return stackGrantBrowseIngresses(job)
	})

	ucxapi.StackGrantBrowseServices.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantBrowseServicesRequest) ([]orc.Service, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return stackGrantBrowseServices(job)
	})

	ucxapi.StackGrantBrowseJobs.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantBrowseJobsRequest) ([]orc.Job, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return stackGrantBrowseJobs(job)
	})

	ucxapi.StackGrantIngressProducts.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantIngressProductsRequest) ([]orc.IngressSupport, *util.HttpError) {
		_, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return shared.LinkSupport, nil
	})

	ucxapi.StackGrantServiceProducts.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantServiceProductsRequest) ([]orc.ServiceSupport, *util.HttpError) {
		_, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return shared.ServiceSupport, nil
	})

	ucxapi.StackGrantServiceUpdateMembers.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantServiceUpdateMembersRequest) (util.Empty, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return util.Empty{}, err
		}

		_, invokeErr := orc.ServicesControlUpdateMembers.Invoke(orc.ServicesControlUpdateMembersRequest{
			JobId:         job.Id,
			Id:            request.Id,
			AddedJobIds:   request.AddedJobIds,
			RemovedJobIds: request.RemovedJobIds,
		})
		if invokeErr != nil {
			return util.Empty{}, invokeErr
		}

		return util.Empty{}, nil
	})

	ucxapi.StackGrantDeleteIngress.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantDeleteIngressRequest) (util.Empty, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return util.Empty{}, err
		}

		services, servicesErr := stackGrantBrowseServices(job)
		if servicesErr != nil {
			return util.Empty{}, servicesErr
		}

		if !slices.ContainsFunc(services, func(svc orc.Service) bool { return svc.Id == request.ServiceId }) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "the service does not belong to the stack")
		}

		ingresses, ingressesErr := stackGrantBrowseIngresses(job)
		if ingressesErr != nil {
			return util.Empty{}, ingressesErr
		}

		ids := []string{}
		for _, ingress := range ingresses {
			target := ingress.Specification.Target
			targetsOurService := target.Present && target.Value.ServiceId == request.ServiceId
			if targetsOurService && slices.Contains(request.IngressIds, ingress.Id) {
				ids = append(ids, ingress.Id)
			}
		}

		if len(ids) != len(request.IngressIds) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "one or more public links do not target the service")
		}

		_, invokeErr := orc.IngressesControlDelete.Invoke(orc.IngressesControlDeleteRequest{
			JobId:      job.Id,
			IngressIds: ids,
		})
		if invokeErr != nil {
			return util.Empty{}, invokeErr
		}

		return util.Empty{}, nil
	})
}

func stackGrantBrowseIngresses(job *orc.Job) ([]orc.Ingress, *util.HttpError) {
	return stackGrantBrowseStack(
		job,
		func(next util.Option[string], flags orc.ResourceFlags) (fnd.PageV2[orc.Ingress], *util.HttpError) {
			return orc.IngressesControlBrowse.Invoke(orc.IngressesControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
				IngressFlags: orc.IngressFlags{ResourceFlags: flags},
			})
		},
		func(item orc.Ingress) orc.ResourceOwner {
			return item.Owner
		},
	)
}

func stackGrantBrowseServices(job *orc.Job) ([]orc.Service, *util.HttpError) {
	return stackGrantBrowseStack(
		job,
		func(next util.Option[string], flags orc.ResourceFlags) (fnd.PageV2[orc.Service], *util.HttpError) {
			return orc.ServicesControlBrowse.Invoke(orc.ServicesControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
				ServiceFlags: orc.ServiceFlags{ResourceFlags: flags},
			})
		},
		func(item orc.Service) orc.ResourceOwner {
			return item.Owner
		},
	)
}

func stackGrantBrowseJobs(job *orc.Job) ([]orc.Job, *util.HttpError) {
	return stackGrantBrowseStack(
		job,
		func(next util.Option[string], flags orc.ResourceFlags) (fnd.PageV2[orc.Job], *util.HttpError) {
			return orc.JobsControlBrowse.Invoke(orc.JobsControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
				JobFlags:     orc.JobFlags{ResourceFlags: flags},
			})
		},
		func(item orc.Job) orc.ResourceOwner {
			return item.Owner
		},
	)
}

func stackGrantBrowseStack[Resc any](
	job *orc.Job,
	browsePage func(next util.Option[string], flags orc.ResourceFlags) (fnd.PageV2[Resc], *util.HttpError),
	ownerOf func(Resc) orc.ResourceOwner,
) ([]Resc, *util.HttpError) {
	stackInstance := job.Specification.Labels[orc.ResourceLabelStackInstance]
	flags := orc.ResourceFlags{
		FilterLabels: map[string]string{
			orc.ResourceLabelStackInstance: stackInstance,
		},
	}

	result := []Resc{}
	next := util.OptNone[string]()
	for {
		page, invokeErr := browsePage(next, flags)
		if invokeErr != nil {
			return nil, invokeErr
		}

		for _, item := range page.Items {
			owner := ownerOf(item)
			sameOwner := owner.CreatedBy == job.Owner.CreatedBy &&
				owner.Project.Value == job.Owner.Project.Value
			if sameOwner {
				result = append(result, item)
			}
		}

		next = page.Next
		if !next.Present || len(page.Items) == 0 {
			break
		}
	}

	return result, nil
}

func stackGrantCreateServer[Spec any](
	call rpc.Call[ucxapi.StackGrantCreateRequest[Spec], fnd.BulkResponse[fnd.FindByStringId]],
	create rpc.Call[orc.ControlCreateRequest[Spec], fnd.BulkResponse[fnd.FindByStringId]],
	mergeLabels func(*Spec, map[string]string),
) {
	call.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreateRequest[Spec]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			mergeLabels(&request.Items[i], labels)
		}

		response, invokeErr := create.Invoke(orc.ControlCreateRequest[Spec]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		return response, nil
	})
}
