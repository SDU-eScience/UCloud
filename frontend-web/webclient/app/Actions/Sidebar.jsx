import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_SIDEBAR_LOADING, RECEIVE_SIDEBAR_OPTIONS, SET_SIDEBAR_OPEN } from "../Reducers/Sidebar";

export const fetchSidebarOptions = () => 
    Cloud.get("/../mock-api/mock_sidebar_options.json").then(({ response }) => {
        return receiveSidebarOptions(response);
    });

export const setSidebarLoading = (loading) => ({
    type: SET_SIDEBAR_LOADING,
    loading
});

const receiveSidebarOptions = (options) => ({
    type: RECEIVE_SIDEBAR_OPTIONS,
    options
});

export const setSidebarOpen = () => ({
    type: SET_SIDEBAR_OPEN
});