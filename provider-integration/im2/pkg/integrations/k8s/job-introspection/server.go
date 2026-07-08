package job_introspection

import (
	"context"
	"database/sql"
	"fmt"
	"net/http"

	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func InitServerHandlers() {
	IntrospectJob.Handler(func(info rpc.RequestInfo, request IntrospectAuthRequest) (IntrospectJobResponse, *util.HttpError) {
		jobId, _, ok := Authenticate(request.Token)
		if !ok {
			return IntrospectJobResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		job, ok := ctrl.JobRetrieve(jobId)
		if !ok {
			return IntrospectJobResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		return IntrospectJobResponse{
			Job:       *job,
			ServiceIp: serviceIp(jobId),
		}, nil
	})

	IntrospectNetworks.Handler(func(info rpc.RequestInfo, request IntrospectAuthRequest) (IntrospectNetworksResponse, *util.HttpError) {
		jobId, _, ok := Authenticate(request.Token)
		if !ok {
			return IntrospectNetworksResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		job, ok := ctrl.JobRetrieve(jobId)
		if !ok {
			return IntrospectNetworksResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		networksBySubdomain := map[string]*IntrospectedNetwork{}

		for _, resc := range job.Specification.Resources {
			if resc.Type == orc.AppParameterValueTypePrivateNetwork {
				network, ok := ctrl.PrivateNetworkRetrieve(resc.Id)
				if ok {
					networksBySubdomain[network.Specification.Subdomain] = &IntrospectedNetwork{
						Id:        network.Id,
						Name:      network.Specification.Name,
						Subdomain: network.Specification.Subdomain,
						Members:   nil,
					}
				}
			}
		}

		pods := shared.JobPods.List()
		for _, pod := range pods {
			for subdomain, network := range networksBySubdomain {
				if _, ok := pod.Labels[shared.PrivateNetworkLabel(subdomain)]; ok {
					memberId := pod.Labels["ucloud.dk/jobId"]
					member, ok := ctrl.JobRetrieve(memberId)
					if ok {
						network.Members = append(network.Members, IntrospectedNetworkMember{
							Id:     member.Id,
							Name:   pod.Spec.Hostname,
							Fqdn:   fmt.Sprintf("%s.%s.%s.svc.cluster.local", pod.Spec.Hostname, pod.Spec.Subdomain, shared.ServiceConfig.Compute.Namespace),
							Labels: member.Specification.Labels,
						})
					}
				}
			}
		}

		result := IntrospectNetworksResponse{}
		for _, network := range networksBySubdomain {
			result.Networks = append(result.Networks, *network)
		}

		return result, nil
	})
}

func EnsureToken(jobId string, vmaSrvToken util.Option[string]) string {
	newToken := util.SecureToken()
	token, _ := db.NewTx2(func(tx *db.Transaction) (string, bool) {
		db.Exec(
			tx,
			`
				insert into k8s.job_introspection_tokens(job_id, token, vma_srv_token)
				values (:job_id, :token, :vma_srv_token)
				on conflict (job_id) do nothing
			`,
			db.Params{
				"job_id":        jobId,
				"token":         newToken,
				"vma_srv_token": optStringToSql(vmaSrvToken),
			},
		)

		row, rowOk := db.Get[struct{ Token string }](
			tx,
			`
				select token
				from k8s.job_introspection_tokens
				where job_id = :job_id
			`,
			db.Params{"job_id": jobId},
		)
		return row.Token, rowOk
	})
	return token
}

func StoreToken(jobId string, token string, vmaSrvToken util.Option[string]) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.job_introspection_tokens(job_id, token, vma_srv_token)
				values (:job_id, :token, :vma_srv_token)
				on conflict (job_id) do update set
					token = excluded.token,
					vma_srv_token = excluded.vma_srv_token
			`,
			db.Params{
				"job_id":        jobId,
				"token":         token,
				"vma_srv_token": optStringToSql(vmaSrvToken),
			},
		)
	})
}

func DeleteTokens(jobIds []string) {
	if len(jobIds) == 0 {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from k8s.job_introspection_tokens
				where job_id = some(:job_ids)
			`,
			db.Params{"job_ids": jobIds},
		)
	})
}

func Authenticate(token string) (string, util.Option[string], bool) {
	return db.NewTx3(func(tx *db.Transaction) (string, util.Option[string], bool) {
		row, ok := db.Get[struct {
			JobId       string
			VmaSrvToken sql.NullString
		}](
			tx,
			`
				select job_id, vma_srv_token
				from k8s.job_introspection_tokens
				where token = :token
			`,
			db.Params{"token": token},
		)

		if ok {
			return row.JobId, util.SqlNullStringToOpt(row.VmaSrvToken), true
		}
		return "", util.OptNone[string](), false
	})
}

func serviceIp(jobId string) string {
	service, err := shared.K8sClient.CoreV1().Services(shared.ServiceConfig.Compute.Namespace).Get(
		context.Background(),
		shared.ServiceName(jobId),
		meta.GetOptions{},
	)
	if err != nil || service == nil {
		return ""
	}
	return service.Spec.ClusterIP
}

func optStringToSql(value util.Option[string]) sql.NullString {
	if value.Present {
		return sql.NullString{String: value.Value, Valid: true}
	}
	return sql.NullString{}
}
