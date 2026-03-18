import * as React from "react";
import { useCloudAPI } from "@/Authentication/DataHook";
import { providerBrandingApi, ProviderBrandingResponse } from "@/UCloud/ProviderBrandingApi";
import { useDispatch } from "react-redux";
import { PayloadAction } from "@reduxjs/toolkit";

export const AutomaticProviderBranding: React.FunctionComponent = () => {
    const [providerBrandings, fetchBranding] = useCloudAPI<ProviderBrandingResponse>(
        providerBrandingApi.browse(),
        { providers: {} }
    );

    React.useEffect(() => {
        const intervalId = setInterval(() => {
            fetchBranding(providerBrandingApi.browse());
        }, 1000 * 60 * 60);
        return () => {
            clearInterval(intervalId);
        }
    }, []);

    const dispatch = useDispatch();

    React.useEffect(() => {
        dispatch({type: ADD_PROVIDER_BRANDING, payload: providerBrandings.data});
    }, [providerBrandings.data]);

    return null;
};

const ADD_PROVIDER_BRANDING = "ADD_PROVIDER_BRANDING";
type SetProviderBranding = PayloadAction<ProviderBrandingResponse, typeof ADD_PROVIDER_BRANDING>

type ProviderBrandingAction = SetProviderBranding;

export function initProviderBranding(): ProviderBrandingResponse {
    return {
        providers: {},
    }
}

export function providerBrandingReducer(state: ProviderBrandingResponse = initProviderBranding(), action: ProviderBrandingAction): ProviderBrandingResponse {
    switch (action.type) {
        case ADD_PROVIDER_BRANDING: {
            return action.payload;
        }        
        default:
            return state;
    }
}