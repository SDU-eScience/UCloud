import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_SIDEBAR_LOADING, RECEIVE_SIDEBAR_OPTIONS } from "../Reducers/Sidebar";

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