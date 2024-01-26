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
