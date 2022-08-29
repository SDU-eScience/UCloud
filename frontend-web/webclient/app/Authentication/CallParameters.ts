export interface CallParameters {
    method: string;
    path: string;
    body?: any;
    context?: string;
    withCredentials?: boolean;
    projectOverride?: string;
    accessTokenOverride?: string;
}

