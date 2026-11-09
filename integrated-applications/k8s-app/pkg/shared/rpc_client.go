package shared

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"sync"
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

var RpcGrantErrConflict = errors.New("the application state changed. Try again")

type RpcGrantClient struct {
	Rpc   *rpc.Client
	Token string
}

func RpcClientNew(tokenPath string, timeout time.Duration) (*rpc.Client, string, error) {
	tokenBytes, err := os.ReadFile(tokenPath)
	if err != nil {
		return nil, "", fmt.Errorf("could not read the controller token: %s", err)
	}

	token := strings.TrimSpace(string(tokenBytes))
	if token == "" {
		return nil, "", errors.New("the controller token is empty")
	}

	hostBytes, err := os.ReadFile(RpcGrantProviderHostnamePath)
	if err != nil {
		return nil, "", fmt.Errorf("could not read the provider hostname: %s", err)
	}

	host := strings.TrimSpace(string(hostBytes))
	if host == "" {
		return nil, "", fmt.Errorf("the provider hostname at %s is empty", RpcGrantProviderHostnamePath)
	}

	client := &rpc.Client{
		BasePath: fmt.Sprintf("http://%s:%d", host, RpcGrantProviderPort),
		Client:   &http.Client{Timeout: timeout},
	}

	return client, token, nil
}

var rpcGrantClientCache struct {
	mu     sync.Mutex
	client RpcGrantClient
	valid  bool
}

func RpcGrantClientNew() (RpcGrantClient, error) {
	rpcGrantClientCache.mu.Lock()
	defer rpcGrantClientCache.mu.Unlock()

	if rpcGrantClientCache.valid {
		return rpcGrantClientCache.client, nil
	}

	tokenPath := RpcGrantHostTokenPath
	_, statErr := os.Stat(tokenPath)
	hostTokenMissing := statErr != nil && os.IsNotExist(statErr)
	if hostTokenMissing {
		tokenPath = RpcGrantPodTokenPath
	}

	client, token, err := RpcClientNew(tokenPath, RpcGrantHttpTimeout)
	if err != nil {
		return RpcGrantClient{}, err
	}

	result := RpcGrantClient{
		Rpc:   client,
		Token: token,
	}

	rpcGrantClientCache.client = result
	rpcGrantClientCache.valid = true

	return result, nil
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
	response, herr := ucxapi.StackControlStateWriteDispatch(client.Rpc, client.Token, ucxapi.StackControlStateWriteRequest{
		Key:              key,
		Value:            value,
		ExpectedRevision: expectedRevision,
		Conditions:       conditions,
		ValueConditions:  valueConditions,
		ValidUntil:       validUntil,
	})
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
