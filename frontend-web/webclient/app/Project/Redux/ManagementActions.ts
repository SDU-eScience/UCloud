import { Cloud } from "Authentication/SDUCloudObject";
import { viewProjectMembers } from "Utilities/ProjectUtilities";
import { PROJECT_MANAGEMENT_RECEIVE_MEMBERS, PROJECT_MANAGEMENT_SET_ERROR } from "./ManagementReducer";
import { Error, PayloadAction } from "Types";

export type ManagementActions = ReceiveProjectMembers | SetError

export const fetchProjectMembers = (id: string): Promise<{ type: string }> =>
    Cloud.get(viewProjectMembers(id))
        .then(it => receiveProjectMembers(it))
        .catch(() => ({ type: "" }));


type ReceiveProjectMembers = PayloadAction<typeof PROJECT_MANAGEMENT_RECEIVE_MEMBERS, { members: any}>
const receiveProjectMembers = (members: any): ReceiveProjectMembers => ({
    type: PROJECT_MANAGEMENT_RECEIVE_MEMBERS,
    payload: { members }
});

type SetError = Error<typeof PROJECT_MANAGEMENT_SET_ERROR>
export const setError = (error?: string): SetError => ({
    type: PROJECT_MANAGEMENT_SET_ERROR,
    payload: { error }
})