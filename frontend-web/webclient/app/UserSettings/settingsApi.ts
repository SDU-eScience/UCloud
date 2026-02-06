import {buildQueryString} from "@/Utilities/URIUtilities";

export interface TwoFactorSetupState {
    challengeId?: string;
    qrCode?: string;
    verificationCode: string;
    isConnectedToAccount?: boolean;
}

export interface NotificationSettings {
    jobStarted: boolean,
    jobStopped: boolean,
}

export interface RetrieveNotificationSettingsRequest {
    username?: string,
}

export interface RetrieveNotificationSettingsResponse {
    settings: NotificationSettings,
}

export function retrieveNotificationSettings(
    request: RetrieveNotificationSettingsRequest
): APICallParameters<RetrieveNotificationSettingsRequest, RetrieveNotificationSettingsResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/notifications" + "/retrieveSettings", {username: request.username}),
        parameters: request,
        reloadId: Math.random(),
    };
}

export function updateNotificationSettings(
    request: NotificationSettings
): APICallParameters<NotificationSettings, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/notifications" + "/settings",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}

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

export function invalidateAllSessions(): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: "/auth/sessions",
        context: "",
        withCredentials: true
    };
}
