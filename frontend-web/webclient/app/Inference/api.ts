import {apiUpdate} from "@/Authentication/DataHook";

const baseContext = "/api/jobs/inference";

export interface OpenPlaygroundRequest {
    providerId: string | null;
}

export interface OpenPlaygroundResponse {
    connectTo: string;
    sessionToken: string;
}

export function openPlayground(request: OpenPlaygroundRequest): APICallParameters<OpenPlaygroundRequest, OpenPlaygroundResponse> {
    return apiUpdate(request, baseContext, "openPlayground");
}
