import {APICallParameters} from "Authentication/DataHook";
import {PaginationRequest} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export interface UserSession {
    ipAddress: string;
    userAgent: string;
    createdAt: number;
}

export type ListUserSessionParameters = PaginationRequest;

export function listUserSessions(
    parameters: ListUserSessionParameters
): APICallParameters<ListUserSessionParameters, void> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/auth/sessions", parameters),
        parameters,
        context: ""
    };
}

export function invalidateAllSessions(): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: "/auth/sessions",
        context: "",
        withCredentials: true
    };
}
