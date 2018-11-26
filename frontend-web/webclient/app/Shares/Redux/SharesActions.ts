import { SharesByPath, ShareState, ShareId } from "Shares";
import { Page, PayloadAction, AccessRight, AccessRightValues, Error } from "Types";
import { Cloud } from "Authentication/SDUCloudObject";
import { sharesByPathQuery } from "Utilities/SharesUtilities";

type SharesActions = ReceiveShares;


type ReceiveShares = PayloadAction<typeof RECEIVE_SHARES, { page: Page<SharesByPath> }>
export const receiveShares = (page: Page<SharesByPath>): ReceiveShares => ({
    type: RECEIVE_SHARES,
    payload: { page }
});

export const fetchSharesByPath = (path: string): Promise<any> => {
    return Cloud.get(sharesByPathQuery(path)).then(e => ({ items: [e.response] }));
}

export function retrieveShares(page: Number, itemsPerPage: Number, byState?: ShareState): Promise<ReceiveShares | SetErrorMessage> {
    let url = `/shares?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (byState) url += `&state=${encodeURIComponent(byState)}`;
    return Cloud.get<Page<SharesByPath>>(url).then(({ response }) => receiveShares(response))
        .catch(({ request }) => setErrorMessage(request.message));
}

type SetErrorMessage = Error<typeof SET_SHARES_ERROR>
const setErrorMessage = (error?: string): SetErrorMessage => ({
    type: SET_SHARES_ERROR,
    payload: { error }
})

function acceptShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/accept/${encodeURIComponent(shareId)}`)
}

function revokeShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/revoke/${encodeURIComponent(shareId)}`).then(it => it)
}

function createShare(user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> {
    return Cloud.put(`/shares/`, { sharedWith: user, path, rights }).then(it => it)
}

function updateShare(id: ShareId, rights: AccessRightValues[]): Promise<any> {
    return Cloud.post(`/shares/`, { id, rights }).then(e => e.response).then(it => it);
}