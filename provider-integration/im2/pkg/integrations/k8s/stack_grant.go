package k8s

import (
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
	TokenSalt      []byte
}

func stackGrantTokenCreate(job *orc.Job) string {
	secret := util.SecureToken()
	hashed := util.HashPassword(secret, util.GenSalt())
	stackInstance := job.Specification.Labels[orc.ResourceLabelStackInstance]

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.stack_grant_tokens(
					job_id, stack_instance, owner_created_by, owner_project, provider, token_hash, token_salt
				)
				values (:job_id, :stack_instance, :owner_created_by, :owner_project, :provider, :token_hash, :token_salt)
				on conflict (job_id) do update set
					stack_instance = excluded.stack_instance,
					owner_created_by = excluded.owner_created_by,
					owner_project = excluded.owner_project,
					provider = excluded.provider,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt
			`,
			db.Params{
				"job_id":           job.Id,
				"stack_instance":   stackInstance,
				"owner_created_by": job.Owner.CreatedBy,
				"owner_project":    job.Owner.Project.Sql(),
				"provider":         job.Specification.Product.Provider,
				"token_hash":       hashed.HashedPassword,
				"token_salt":       hashed.Salt,
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

	if job, ok := controller.JobRetrieve(row.JobId); ok && stackGrantJobMatches(job, row) {
		return job, nil
	}

	next := util.OptNone[string]()
	for {
		page, err := orc.JobsControlBrowse.Invoke(orc.JobsControlBrowseRequest{
			ItemsPerPage: 250,
			Next:         next,
			JobFlags: orc.JobFlags{
				ResourceFlags: orc.ResourceFlags{
					FilterProvider:  util.OptValue(row.Provider),
					FilterCreatedBy: util.OptValue(row.OwnerCreatedBy),
					FilterLabels: map[string]string{
						orc.ResourceLabelStackInstance:   row.StackInstance,
						stackGrantControlPlaneGroupLabel: stackGrantControlPlaneGroup,
					},
				},
			},
		})
		if err != nil {
			return nil, stackGrantTokenForbidden()
		}

		for i := range page.Items {
			job := &page.Items[i]
			if stackGrantJobMatches(job, row) {
				return job, nil
			}
		}

		next = page.Next
		if !next.Present || len(page.Items) == 0 {
			break
		}
	}

	return nil, stackGrantTokenForbidden()
}

func stackGrantTokenAuthenticate(rawToken string) (*orc.Job, *util.HttpError) {
	jobId, secret, ok := stackGrantTokenParse(strings.TrimSpace(rawToken))
	if !ok {
		return nil, stackGrantTokenForbidden()
	}

	row, rowOk := db.NewTx2(func(tx *db.Transaction) (stackGrantTokenRow, bool) {
		row, ok := db.Get[stackGrantTokenRow](
			tx,
			`
				select job_id, stack_instance, owner_created_by, owner_project, provider, token_hash, token_salt
				from k8s.stack_grant_tokens
				where job_id = :job_id
			`,
			db.Params{"job_id": jobId},
		)
		row.JobId = jobId
		return row, ok
	})

	if !rowOk || !util.CheckPassword(row.TokenHash, row.TokenSalt, secret) {
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
	ucxapi.StackGrantCreateIngress.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreateIngressRequest) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			request.Items[i].Labels = util.MapMerge(request.Items[i].Labels, labels)
		}

		response, invokeErr := orc.IngressesControlCreate.Invoke(orc.ControlCreateRequest[orc.IngressSpecification]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		return response, nil
	})

	ucxapi.StackGrantCreatePublicIp.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreatePublicIpRequest) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			request.Items[i].Labels = util.MapMerge(request.Items[i].Labels, labels)
		}

		response, invokeErr := orc.PublicIpsControlCreate.Invoke(orc.ControlCreateRequest[orc.PublicIPSpecification]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		return response, nil
	})

	ucxapi.StackGrantCreatePrivateNetwork.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreatePrivateNetworkRequest) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			request.Items[i].Labels = util.MapMerge(request.Items[i].Labels, labels)
		}

		response, invokeErr := orc.PrivateNetworksControlCreate.Invoke(orc.ControlCreateRequest[orc.PrivateNetworkSpecification]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		ids := make([]fnd.FindByStringId, 0, len(response.Responses))
		for _, network := range response.Responses {
			ids = append(ids, fnd.FindByStringId{Id: network.Id})
		}

		return fnd.BulkResponse[fnd.FindByStringId]{Responses: ids}, nil
	})

	ucxapi.StackGrantCreatePrivateNetworkIp.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreatePrivateNetworkIpRequest) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			request.Items[i].Labels = util.MapMerge(request.Items[i].Labels, labels)
		}

		response, invokeErr := orc.PrivateNetworkIpsControlCreate.Invoke(orc.ControlCreateRequest[orc.PrivateNetworkIpSpecification]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		return response, nil
	})

	ucxapi.StackGrantCreateJob.Handler(func(info rpc.RequestInfo, request ucxapi.StackGrantCreateJobRequest) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		job, err := stackGrantTokenAuthenticate(request.Token)
		if err != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, err
		}

		if len(request.Items) == 0 {
			return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "no items supplied")
		}

		labels := stackGrantLabels(job)
		for i := range request.Items {
			request.Items[i].Labels = util.MapMerge(request.Items[i].Labels, labels)
		}

		response, invokeErr := orc.JobsControlCreate.Invoke(orc.ControlCreateRequest[orc.JobSpecification]{
			JobId: job.Id,
			Items: request.Items,
		})
		if invokeErr != nil {
			return fnd.BulkResponse[fnd.FindByStringId]{}, invokeErr
		}

		return response, nil
	})

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
