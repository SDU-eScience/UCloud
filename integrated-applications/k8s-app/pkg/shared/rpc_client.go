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
	response, herr := ucxapi.StackGrantStateRead.InvokeEx(client.Rpc, ucxapi.StackGrantStateReadRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: client.Token},
		Key:            key,
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
	request := ucxapi.StackGrantStateWriteRequest{
		StackGrantAuth:   ucxapi.StackGrantAuth{Token: client.Token},
		Key:              key,
		Value:            value,
		ExpectedRevision: expectedRevision,
	}

	response, herr := ucxapi.StackGrantStateWrite.InvokeEx(client.Rpc, request, rpc.InvokeOpts{})
	if herr != nil {
		return 0, rpcGrantError(herr)
	}
	return response.Revision, nil
}

func RpcGrantStateList(client RpcGrantClient, prefix string, next util.Option[string], itemsPerPage int) (fnd.PageV2[ucxapi.StackStateRecord], error) {
	response, herr := ucxapi.StackGrantStateList.InvokeEx(client.Rpc, ucxapi.StackGrantStateListRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: client.Token},
		Prefix:         prefix,
		Next:           next,
		ItemsPerPage:   itemsPerPage,
	}, rpc.InvokeOpts{})
	if herr != nil {
		return fnd.PageV2[ucxapi.StackStateRecord]{}, rpcGrantError(herr)
	}
	return response, nil
}
