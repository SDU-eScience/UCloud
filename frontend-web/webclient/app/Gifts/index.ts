import {ResourceRequest} from "Project/Grant";

export type AvailableGiftsRequest = any;

interface Gift {
    id: number;
    title: string;
    description: string;
    resources: ResourceRequest[];
    resourcesOwnedBy: string;
}

export interface AvailableGiftsResponse {
    gifts: Gift[];
}

export function availableGifts(request: AvailableGiftsRequest): APICallParameters<AvailableGiftsRequest> {
    return {
        method: "GET",
        path: "/gifts/available",
        parameters: request,
        reloadId: Math.random()
    };
}

export interface ClaimGiftRequest {
    giftId: number;
}

export type ClaimGiftResponse = any;

export function claimGift(request: ClaimGiftRequest): APICallParameters<ClaimGiftRequest> {
    return {
        method: "POST",
        path: "/gifts/claim",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}
