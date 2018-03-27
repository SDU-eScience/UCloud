import { UPDATE_PAGE_TITLE, UPDATE_STATUS } from "../Reducers/Status";

export const updatePageTitle = (title) => ({
    type: UPDATE_PAGE_TITLE,
    title
});

export const updateStatus = (status) => ({
    type: UPDATE_STATUS,
    status
});