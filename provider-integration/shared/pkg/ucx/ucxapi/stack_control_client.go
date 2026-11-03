package ucxapi

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func StackControlInvoke[CtrlReq any, Resp any](
	client *rpc.Client,
	token string,
	call *rpc.Call[StackControlRequestOf[CtrlReq], Resp],
	request CtrlReq,
) (Resp, *util.HttpError) {
	return call.InvokeEx(client, StackControlRequestOf[CtrlReq]{
		StackCredentialAuth: StackCredentialAuth{Token: token},
		Request:             request,
	}, rpc.InvokeOpts{})
}

func StackControlStateWriteDispatch(
	client *rpc.Client,
	token string,
	request StackControlStateWriteRequest,
) (StackStateWriteResponse, *util.HttpError) {
	request.StackCredentialAuth = StackCredentialAuth{Token: token}

	call := &StackControlStateWrite
	if len(request.Conditions) > 0 || request.ValidUntil.Present {
		call = &StackControlStateWriteChecked
	}
	if len(request.ValueConditions) > 0 {
		call = &StackControlStateWriteCheckedValues
	}
	return call.InvokeEx(client, request, rpc.InvokeOpts{})
}

func StackControlBrowseAll[CtrlReq any, Item any](
	client *rpc.Client,
	token string,
	call *rpc.Call[StackControlRequestOf[CtrlReq], fnd.PageV2[Item]],
	request func(next util.Option[string]) CtrlReq,
) ([]Item, *util.HttpError) {
	result := []Item{}
	next := util.OptNone[string]()
	for {
		page, herr := StackControlInvoke(client, token, call, request(next))
		if herr != nil {
			return nil, herr
		}

		result = append(result, page.Items...)
		next = page.Next
		if !next.Present || len(page.Items) == 0 {
			return result, nil
		}
	}
}
