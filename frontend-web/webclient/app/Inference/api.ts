import {apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

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

export type InferenceCapability = "TextGeneration" | "TextToImage" | "SpeechToText";

export interface InferenceModel {
    name: string;
    title: string;
    capabilities: InferenceCapability[];
    priceMultiplier: {
        cachedInput: number;
        input: number;
        output: number;
    };
    endpoint: {
        basePath: string;
        backendModelName: string;
    };
    availability: {
        public: boolean;
        availableTo: string[];
    };
    contextWindow?: number;
    chatSettings: {
        temperature: number;
        topP: number;
        maxCompletionTokens: number;
        systemPrompt?: string;
    };
}

export interface ListModelsRequest {
    providerId: string | null;
}

export interface ListModelsResponse {
    models: InferenceModel[];
    isAdmin: boolean;
    providerId: string;
}

export interface UpdateModelRequest {
    providerId: string | null;
    oldName: string;
    model: InferenceModel;
}

export function listModels(request: ListModelsRequest): APICallParameters<ListModelsRequest, ListModelsResponse> {
    return apiRetrieve(request, baseContext, "models");
}

export function updateModel(request: UpdateModelRequest): APICallParameters<UpdateModelRequest, Record<string, never>> {
    return apiUpdate(request, baseContext, "model");
}
