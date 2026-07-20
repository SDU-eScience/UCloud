package job_introspection

import (
	"context"
	"database/sql"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"time"

	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	syncthing_metrics "ucloud.dk/pkg/integrations/k8s/syncthing-metrics"
	"ucloud.dk/pkg/ucxdelivery"
	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func InitServerHandlers() {
	syncthing_metrics.Publish.Handler(func(info rpc.RequestInfo, request syncthing_metrics.PublishRequest) (syncthing_metrics.PublishResponse, *util.HttpError) {
		jobId, _, ok := Authenticate(request.Token)
		if !ok {
			return syncthing_metrics.PublishResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}
		job, ok := ctrl.JobRetrieve(jobId)
		if !ok || job.Specification.Application.Name != "syncthing" || job.Status.State != orc.JobStateRunning {
			syncthing_metrics.SyncthingSnapshots.Remove([]string{jobId})
			return syncthing_metrics.PublishResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}
		if !syncthing_metrics.ValidSyncthingSnapshot(request.Snapshot) {
			return syncthing_metrics.PublishResponse{}, util.HttpErr(http.StatusBadRequest, "invalid Syncthing metrics snapshot")
		}
		syncthing_metrics.SyncthingSnapshots.Publish(jobId, request.Snapshot, time.Now())
		return syncthing_metrics.PublishResponse{}, nil
	})

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

	UcxPublish.Handler(func(info rpc.RequestInfo, request UcxPublishRequest) (UcxPublishResponse, *util.HttpError) {
		jobId, _, ok := Authenticate(request.Token)
		if !ok {
			return UcxPublishResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		job, ok := ctrl.JobRetrieve(jobId)
		if !ok {
			return UcxPublishResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		appName := strings.TrimSpace(request.AppName)
		appVersion := strings.TrimSpace(request.AppVersion)
		projects := shared.ServiceConfig.Compute.Ucx.Publish[appName]
		project := job.Owner.Project.Value
		if project == "" || len(projects) == 0 || !slices.Contains(projects, project) {
			return UcxPublishResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		ucloudPath, ok := shared.ContainerPathToUCloudFileMount(job, request.ContainerPath)
		if !ok {
			return UcxPublishResponse{}, util.HttpErr(http.StatusBadRequest, "container path is not part of a UCloud file mount")
		}

		internalPath, ok, _ := filesystem.UCloudToInternal(ucloudPath)
		if !ok {
			return UcxPublishResponse{}, util.HttpErr(http.StatusBadRequest, "could not resolve UCloud path")
		}

		result, err := ucxdelivery.PublishVersion(shared.ServiceConfig.FileSystem.MountPoint, appName, appVersion, internalPath)
		if err != nil {
			return UcxPublishResponse{}, util.HttpErr(http.StatusBadRequest, "%s", err.Error())
		}

		return UcxPublishResponse{
			UCloudPath: ucloudPath,
			BinaryName: result.BinaryName,
		}, nil
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
	syncthing_metrics.SyncthingSnapshots.Remove(jobIds)

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
