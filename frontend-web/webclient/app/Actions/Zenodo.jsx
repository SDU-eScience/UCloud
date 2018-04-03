import { Cloud } from "../../authentication/SDUCloudObject";
import { RECEIVE_PUBLICATIONS, SET_LOADING } from "../Reducers/Zenodo";

export const fetchPublications = () =>
    Cloud.get("/zenodo/publications").then(({ response }) => {
        return receivePublications(response.inProgress, response.connected);
    }).catch(failure => {
        return receivePublications([], false);
    });

const receivePublications = (publications, connected) => ({
    type: RECEIVE_PUBLICATIONS,
    publications,
    connected
})

export const setLoading = (loading) => ({
    type: SET_LOADING,
    loading
});