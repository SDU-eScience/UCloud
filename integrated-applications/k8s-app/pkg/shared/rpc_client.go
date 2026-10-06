package shared

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const RpcGrantProviderHostnamePath = "/opt/ucloud/provider-hostname.txt"

const RpcGrantHostTokenPath = "/var/lib/ucloud-k8s/controller/token"

const RpcGrantPodTokenPath = "/etc/ucloud-k8s/controller/token"

const RpcGrantProviderPort = 42000

const RpcGrantHttpTimeout = 10 * time.Second

var RpcGrantErrConflict = errors.New("stack state revision or lease conflict")

type RpcGrantClient struct {
	Rpc   *rpc.Client
	Token string
}

func RpcGrantClientNew() (RpcGrantClient, error) {
	tokenBytes, hostErr := os.ReadFile(RpcGrantHostTokenPath)
	if hostErr != nil {
		if !os.IsNotExist(hostErr) {
			return RpcGrantClient{}, fmt.Errorf("could not read the controller token: %s", hostErr)
		}
		podBytes, podReadErr := os.ReadFile(RpcGrantPodTokenPath)
		if podReadErr != nil {
			return RpcGrantClient{}, fmt.Errorf("could not read the controller token: %s", podReadErr)
		}
		tokenBytes = podBytes
	}

	token := strings.TrimSpace(string(tokenBytes))
	if token == "" {
		return RpcGrantClient{}, errors.New("the controller token is empty")
	}

	hostBytes, err := os.ReadFile(RpcGrantProviderHostnamePath)
	if err != nil {
		return RpcGrantClient{}, fmt.Errorf("could not read the provider hostname: %s", err)
	}

	host := strings.TrimSpace(string(hostBytes))
	if host == "" {
		return RpcGrantClient{}, fmt.Errorf("the provider hostname at %s is empty", RpcGrantProviderHostnamePath)
	}

	return RpcGrantClient{
		Rpc: &rpc.Client{
			BasePath: fmt.Sprintf("http://%s:%d", host, RpcGrantProviderPort),
			Client:   &http.Client{Timeout: RpcGrantHttpTimeout},
		},
		Token: token,
	}, nil
}

func rpcGrantError(herr *util.HttpError) error {
	if herr != nil && herr.StatusCode == http.StatusConflict {
		return RpcGrantErrConflict
	}
	return fmt.Errorf("the stack grant call failed: %s", herr)
}

func RpcGrantStateRead(client RpcGrantClient, key string) (ucxapi.StackStateRecord, bool, error) {
	response, herr := ucxapi.StackControlStateRead.InvokeEx(client.Rpc, ucxapi.StackControlStateReadRequest{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Key:                 key,
	}, rpc.InvokeOpts{})
	if herr != nil {
		return ucxapi.StackStateRecord{}, false, rpcGrantError(herr)
	}

	if !response.Found {
		return ucxapi.StackStateRecord{}, false, nil
	}

	return response.Record, true, nil
}

func RpcGrantStateWrite(client RpcGrantClient, key string, value json.RawMessage, expectedRevision int64) (int64, error) {
	return RpcGrantStateWriteChecked(client, key, value, expectedRevision, nil, nil, util.OptNone[int64]())
}

func RpcGrantStateWriteChecked(
	client RpcGrantClient,
	key string,
	value json.RawMessage,
	expectedRevision int64,
	conditions []ucxapi.StackStateRevisionCondition,
	valueConditions []ucxapi.StackStateValueCondition,
	validUntil util.Option[int64],
) (int64, error) {
	request := ucxapi.StackControlStateWriteRequest{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Key:                 key,
		Value:               value,
		ExpectedRevision:    expectedRevision,
		Conditions:          conditions,
		ValueConditions:     valueConditions,
		ValidUntil:          validUntil,
	}

	call := ucxapi.StackControlStateWrite
	if len(conditions) > 0 || validUntil.Present {
		call = ucxapi.StackControlStateWriteChecked
	}
	if len(valueConditions) > 0 {
		call = ucxapi.StackControlStateWriteCheckedValues
	}
	response, herr := call.InvokeEx(client.Rpc, request, rpc.InvokeOpts{})
	if herr != nil {
		return 0, rpcGrantError(herr)
	}
	return response.Revision, nil
}

func RpcGrantStateList(client RpcGrantClient, prefix string, next util.Option[string], itemsPerPage int) (fnd.PageV2[ucxapi.StackStateRecord], error) {
	response, herr := ucxapi.StackControlStateList.InvokeEx(client.Rpc, ucxapi.StackControlStateListRequest{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Prefix:              prefix,
		Next:                next,
		ItemsPerPage:        itemsPerPage,
	}, rpc.InvokeOpts{})
	if herr != nil {
		return fnd.PageV2[ucxapi.StackStateRecord]{}, rpcGrantError(herr)
	}
	return response, nil
}

func rpcGrantBrowseAll[Resc any](
	browsePage func(next util.Option[string]) (fnd.PageV2[Resc], *util.HttpError),
) ([]Resc, error) {
	result := []Resc{}
	next := util.OptNone[string]()
	for {
		page, herr := browsePage(next)
		if herr != nil {
			return nil, rpcGrantError(herr)
		}

		result = append(result, page.Items...)
		next = page.Next
		if !next.Present || len(page.Items) == 0 {
			return result, nil
		}
	}
}

func RpcGrantBrowseIngresses(client *rpc.Client, token string) ([]orcapi.Ingress, error) {
	return rpcGrantBrowseAll(func(next util.Option[string]) (fnd.PageV2[orcapi.Ingress], *util.HttpError) {
		return ucxapi.StackControlBrowseIngresses.InvokeEx(client, ucxapi.StackControlRequestOf[orcapi.IngressesControlBrowseRequest]{
			StackCredentialAuth: ucxapi.StackCredentialAuth{Token: token},
			Request: orcapi.IngressesControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
			},
		}, rpc.InvokeOpts{})
	})
}

func RpcGrantBrowseServices(client *rpc.Client, token string) ([]orcapi.Service, error) {
	return rpcGrantBrowseAll(func(next util.Option[string]) (fnd.PageV2[orcapi.Service], *util.HttpError) {
		return ucxapi.StackControlBrowseServices.InvokeEx(client, ucxapi.StackControlRequestOf[orcapi.ServicesControlBrowseRequest]{
			StackCredentialAuth: ucxapi.StackCredentialAuth{Token: token},
			Request: orcapi.ServicesControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
			},
		}, rpc.InvokeOpts{})
	})
}

func RpcGrantBrowseJobs(client *rpc.Client, token string) ([]orcapi.Job, error) {
	return rpcGrantBrowseAll(func(next util.Option[string]) (fnd.PageV2[orcapi.Job], *util.HttpError) {
		return ucxapi.StackControlBrowseJobs.InvokeEx(client, ucxapi.StackControlRequestOf[orcapi.JobsControlBrowseRequest]{
			StackCredentialAuth: ucxapi.StackCredentialAuth{Token: token},
			Request: orcapi.JobsControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
			},
		}, rpc.InvokeOpts{})
	})
}

func RpcGrantRetrieveJob(client RpcGrantClient, jobId string) (orcapi.Job, bool, *util.HttpError) {
	job, herr := ucxapi.StackControlRetrieveJob.InvokeEx(client.Rpc, ucxapi.StackControlRequestOf[orcapi.JobsControlRetrieveRequest]{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Request:             orcapi.JobsControlRetrieveRequest{Id: jobId},
	}, rpc.InvokeOpts{})
	if herr != nil {
		return orcapi.Job{}, false, herr
	}
	return job, true, nil
}

func RpcGrantTerminateJob(client RpcGrantClient, jobId string) *util.HttpError {
	_, herr := ucxapi.StackControlTerminateJobs.InvokeEx(client.Rpc, ucxapi.StackControlRequestOf[orcapi.ControlMutateRequest[fnd.FindByStringId]]{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Request: orcapi.ControlMutateRequest[fnd.FindByStringId]{
			Items: []fnd.FindByStringId{{Id: jobId}},
		},
	}, rpc.InvokeOpts{})
	return herr
}

func RpcGrantDeletePrivateNetworkIp(client RpcGrantClient, reservationId string) *util.HttpError {
	_, herr := ucxapi.StackControlDeletePrivateNetworkIp.InvokeEx(client.Rpc, ucxapi.StackControlRequestOf[orcapi.ControlMutateRequest[fnd.FindByStringId]]{
		StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
		Request: orcapi.ControlMutateRequest[fnd.FindByStringId]{
			Items: []fnd.FindByStringId{{Id: reservationId}},
		},
	}, rpc.InvokeOpts{})
	return herr
}
