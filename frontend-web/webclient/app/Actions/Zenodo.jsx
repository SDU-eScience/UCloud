import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    SET_ZENODO_LOADING
} from "../Reducers/Zenodo";

export const fetchPublications = (page, itemsPerPage) =>
    Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`).then(({ response }) => {
        return receivePublications(response.inProgress, response.connected);
    }).catch(failure => {
        return receivePublications([], false);
    });

const receivePublications = (page, connected) => ({
    type: RECEIVE_PUBLICATIONS,
    connected,
    page
})

export const setZenodoLoading = (loading) => ({
    type: SET_ZENODO_LOADING,
    loading
});