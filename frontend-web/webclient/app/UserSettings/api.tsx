import {buildQueryString} from "Utilities/URIUtilities";
import {BulkRequest} from "UCloud";

export interface UserSession {
    ipAddress: string;
    userAgent: string;
    createdAt: number;
}

export type ListUserSessionParameters = PaginationRequest;

export function listUserSessions(
    parameters: ListUserSessionParameters
): APICallParameters<ListUserSessionParameters> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/auth/sessions", parameters),
        parameters,
        context: ""
    };
}
/*
export interface EmailSettings {
    //Grant applications
    newGrantApplication: boolean;
    grantAutoApprove: boolean;
    grantApplicationUpdated: boolean;
    grantApplicationApproved: boolean;
    grantApplicationRejected: boolean;
    grantApplicationWithdrawn: boolean;
    newCommentOnApplication: boolean;
    applicationTransfer: boolean;
    applicationStatusChange: boolean;
    //Project
    projectUserInvite: boolean;
    projectUserRemoved: boolean;
    verificationReminder: boolean;
    userRoleChange: boolean;
    userLeft: boolean;
    lowFunds: boolean;
}

export interface RetrieveEmailSettingsRequest {
    username?: string
}

export function retrieveEmailSettings(
    request: RetrieveEmailSettingsRequest
):APICallParameters<RetrieveEmailSettingsRequest> {
    return {
        context: "",
        method: "GET",
        path: "/api/mail" + "/retrieveEmailSettings",
        parameters: request,
        reloadId: Math.random(),
    }
}

export interface ToggleEmailSettingsRequestItem {
    username?: string,
    settings: EmailSettings
}
*/
//export function toggleEmailSettings(
//    request: BulkRequest<ToggleEmailSettingsRequestItem>
//): APICallParameters<BulkRequest<ToggleEmailSettingsRequestItem>, any /* unknown */> {
//    return {
//        context: "",
//        method: "POST",
//        path: "/api/mail" + "/toggleEmailSettings",
//        parameters: request,
//        reloadId: Math.random(),
//        payload: request,
//    };
//}

export function invalidateAllSessions(): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: "/auth/sessions",
        context: "",
        withCredentials: true
    };
}
