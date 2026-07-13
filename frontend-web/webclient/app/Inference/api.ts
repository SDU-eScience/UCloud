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

export interface PlaygroundThread {
    id: string;
    title: string;
    updatedAt: number;
}

export interface ListPlaygroundThreadsRequest {
    providerId: string | null;
}

export interface ListPlaygroundThreadsResponse {
    threads: PlaygroundThread[];
}

export function listPlaygroundThreads(request: ListPlaygroundThreadsRequest): APICallParameters<ListPlaygroundThreadsRequest, ListPlaygroundThreadsResponse> {
    return apiRetrieve(request, baseContext, "playgroundThreads");
}

export type InferenceCapability = "TextGeneration" | "TextToImage" | "SpeechToText" | "Vision" | "VideoVision" | "Audio";

export interface InferenceModel {
    name: string;
    title: string;
    titleModelName: string;
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
    page?: InferenceModelPageMetadata;
}

export interface InferenceModelPageMetadata {
    shortDescription?: string;
    documentationUrl?: string;
    releaseDate?: number;
    about?: {
        description?: string;
        highlights?: string[];
        keyStats?: InferenceModelKeyStat[];
    };
    benchmarkScores?: Record<string, string>;
    datasheet?: {
        parameters?: string;
        activatedParameters?: string;
        quantization?: string;
    };
}

export interface InferenceModelKeyStat {
    label: string;
    value: string;
    description?: string;
}

export interface InferenceBenchmark {
    id: string;
    title: string;
    description?: string;
    higherIsBetter: boolean;
    modelNames: string[];
}

export interface ListModelsRequest {
    providerId: string | null;
}

export interface ListModelsResponse {
    models: InferenceModel[];
    benchmarks: InferenceBenchmark[];
    isAdmin: boolean;
    providerId: string;
    server: string;
}

export interface UpdateModelRequest {
    providerId: string | null;
    oldName: string;
    model: InferenceModel;
}

export interface UpdateBenchmarksRequest {
    providerId: string | null;
    benchmarks: InferenceBenchmark[];
}

export function listModels(request: ListModelsRequest): APICallParameters<ListModelsRequest, ListModelsResponse> {
    return apiRetrieve(request, baseContext, "models");
}

export function updateModel(request: UpdateModelRequest): APICallParameters<UpdateModelRequest, Record<string, never>> {
    return apiUpdate(request, baseContext, "model");
}

export function updateBenchmarks(request: UpdateBenchmarksRequest): APICallParameters<UpdateBenchmarksRequest, Record<string, never>> {
    return apiUpdate(request, baseContext, "benchmarks");
}
