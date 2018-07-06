import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    SET_ZENODO_LOADING
} from "../Reducers/Zenodo";
import { SetLoadingAction, ReceivePage } from "../types/types";

export const fetchPublications = (page: number, itemsPerPage: number): Promise<ReceivePublications> =>
    Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`).then(({ response }) => {
        return receivePublications(response.inProgress, response.connected);
    }).catch(failure => {
        return receivePublications([], false);
    });

interface ReceivePublications extends ReceivePage<File> { connected: boolean }
const receivePublications = (page, connected): ReceivePublications => ({
    type: RECEIVE_PUBLICATIONS,
    connected,
    page
})

export const setZenodoLoading = (loading): SetLoadingAction => ({
    type: SET_ZENODO_LOADING,
    loading
});