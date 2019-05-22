import { SharesByPath, ShareState } from "Shares";
import { Page, PayloadAction, Error, SetLoadingAction } from "Types";
import { Cloud } from "Authentication/SDUCloudObject";
import { sharesByPathQuery } from "Utilities/SharesUtilities";
import { RECEIVE_SHARES, SET_SHARES_ERROR, SET_SHARE_STATE, SET_SHARES_LOADING } from "./SharesReducer";

export type SharesActions = ReceiveShares | SetErrorMessage | SetShareState | SetLoading;

type ReceiveShares = PayloadAction<typeof RECEIVE_SHARES, { page: Page<SharesByPath> }>
export const receiveShares = (page: Page<SharesByPath>): ReceiveShares => ({
    type: RECEIVE_SHARES,
    payload: { page }
});


export const fetchSharesByPath = async (path: string): Promise<ReceiveShares | SetErrorMessage> => {
    try {
        const { response } = await Cloud.get(sharesByPathQuery(path));
        return receiveShares({
            itemsInTotal: response.length,
            itemsPerPage: 25,
            pagesInTotal: 1,
            pageNumber: 0,
            items: [response],
        });
    } catch ({ request }) {
        return setErrorMessage(request.message)
    }
};

export const retrieveShares = async (page: Number, itemsPerPage: Number, byState?: ShareState): Promise<ReceiveShares | SetErrorMessage> => {
    let url = `/shares?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (byState) url += `&state=${encodeURIComponent(byState)}`;
    try {
        const r = await Cloud.get<Page<SharesByPath>>(url);
        return receiveShares(r.response);
    } catch ({ request }) {
        return setErrorMessage(request.message);
    }
};

type SetShareState = PayloadAction<typeof SET_SHARE_STATE, { sharedByMe: boolean }>
export const setShareState = (sharedByMe: boolean): SetShareState => ({
    type: SET_SHARE_STATE,
    payload: { sharedByMe }
});

type SetErrorMessage = Error<typeof SET_SHARES_ERROR>
export const setErrorMessage = (error?: string): SetErrorMessage => ({
    type: SET_SHARES_ERROR,
    payload: { error }
});

type SetLoading = SetLoadingAction<typeof SET_SHARES_LOADING>
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_SHARES_LOADING,
    payload: { loading }
});