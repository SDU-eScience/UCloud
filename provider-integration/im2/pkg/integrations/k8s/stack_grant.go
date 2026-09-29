package k8s

import (
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
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

func stackGrantTokenCreate(job *orc.Job) string {
	secret := util.SecureToken()
	stackInstance := job.Specification.Labels[orc.ResourceLabelStackInstance]

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.stack_grant_tokens(
					job_id, stack_instance, owner_created_by, owner_project, provider, token_hash
				)
				values (:job_id, :stack_instance, :owner_created_by, :owner_project, :provider, :token_hash)
				on conflict (job_id) do update set
					stack_instance = excluded.stack_instance,
					owner_created_by = excluded.owner_created_by,
					owner_project = excluded.owner_project,
					provider = excluded.provider,
					token_hash = excluded.token_hash
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

	return stackGrantTokenPrefix + job.Id + "-" + secret
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

func stackGrantJobMatches(job *orc.Job, row *stackGrantTokenRow) bool {
	if job.Status.State.IsFinal() {
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

	return job.Specification.Labels[stackGrantControlPlaneGroupLabel] == stackGrantControlPlaneGroup
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

	ucxapi.StackGrantBrowseIngresses.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantBrowseIngressesRequest) ([]orc.Ingress, *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		stackInstance := job.Specification.Labels[orc.ResourceLabelStackInstance]
		flags := orc.ResourceFlags{
			FilterLabels: map[string]string{
				orc.ResourceLabelStackInstance: stackInstance,
			},
		}

		result := []orc.Ingress{}
		next := util.OptNone[string]()
		for {
			page, invokeErr := orc.IngressesControlBrowse.Invoke(orc.IngressesControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
				IngressFlags: orc.IngressFlags{ResourceFlags: flags},
			})
			if invokeErr != nil {
				return nil, invokeErr
			}

			for _, ingress := range page.Items {
				sameOwner := ingress.Owner.CreatedBy == job.Owner.CreatedBy &&
					ingress.Owner.Project.Value == job.Owner.Project.Value
				if sameOwner {
					result = append(result, ingress)
				}
			}

			next = page.Next
			if !next.Present || len(page.Items) == 0 {
				break
			}
		}

		return result, nil
	})

	ucxapi.StackGrantIngressProducts.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantIngressProductsRequest) ([]orc.IngressSupport, *util.HttpError) {
		_, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return nil, err
		}

		return shared.LinkSupport, nil
	})
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
