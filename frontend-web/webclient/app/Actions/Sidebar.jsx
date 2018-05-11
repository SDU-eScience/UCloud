import { Cloud } from "../../authentication/SDUCloudObject";
import * as Types from "../Reducers/Sidebar";

export const fetchSidebarOptions = () => 
    Cloud.get("/../mock-api/mock_sidebar_options.json").then(({ response }) => {
        return receiveSidebarOptions(response);
    });

export const setSidebarLoading = (loading) => ({
    type: Types.SET_SIDEBAR_LOADING,
    loading
});

const receiveSidebarOptions = (options) => ({
    type: Types.RECEIVE_SIDEBAR_OPTIONS,
    options
});

export const setSidebarOpen = () => ({
    type: Types.SET_SIDEBAR_OPEN
});

export const setSidebarClosed = () => ({
    type: Types.SET_SIDEBAR_CLOSED
})