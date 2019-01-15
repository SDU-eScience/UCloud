import { ManagementActions } from "./ManagementActions";

export const PROJECT_MANAGEMENT_RECEIVE_MEMBERS = "PROJECT_MANAGEMENTS_RECEIVE_MEMBERS";
export const PROJECT_MANAGEMENT_SET_ERROR = "PROJECT_MANAGEMENT_SET_ERROR";

const Management = (state, action: ManagementActions) => {
    switch (action.type) {
        case PROJECT_MANAGEMENT_SET_ERROR:
        case PROJECT_MANAGEMENT_RECEIVE_MEMBERS: {
            return { ...state, ...action.payload };
        }
        default:
            return state;
    }
}