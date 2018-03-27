import { Cloud } from "../../authentication/SDUCloudObject";
import { RECEIVE_APPLICATIONS, SET_LOADING, UPDATE_APPLICATIONS_PER_PAGE } from "../Reducers/Applications";

const receiveApplications = (applications) => ({
    type: RECEIVE_APPLICATIONS,
    applications
});

export const fetchApplications = () =>
    Cloud.get("/hpc/apps").then(({ response }) => {
        response.sort((a, b) =>
            a.prettyName.localeCompare(b.prettyName)
        );
        return receiveApplications(response);
    });

export const setLoading = (loading) => ({
    type: SET_LOADING,
    loading
});

export const toPage = (pageNumber) => ({
    type: TO_PAGE,
    pageNumber
});

export const updateApplications = (applications) => ({
    type: UPDATE_APPLICATIONS,
    applications
});

export const updateApplicationsPerPage = (applicationsPerPage) => ({
    type: UPDATE_APPLICATIONS_PER_PAGE,
    applicationsPerPage
});