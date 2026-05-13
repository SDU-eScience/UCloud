import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {providerBrandingApi, ProviderBrandingResponse} from "@/UCloud/ProviderBrandingApi";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";
import {useDispatch} from "react-redux";

export const AutomaticProviderBranding: React.FunctionComponent = () => {
    const [providerBrandings, fetchBranding] = useCloudAPI<ProviderBrandingResponse>(
        providerBrandingApi.browse(),
        {providers: {}}
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
        dispatch(addProviderBranding(providerBrandings.data));
    }, [providerBrandings.data]);

    return null;
};


export function initProviderBranding(): ProviderBrandingResponse {
    return {
        providers: {},
    }
}

const providerBrandingSlice = createSlice({
    name: "providerBranding",
    initialState: initProviderBranding(),
    reducers: {
        addProviderBranding(state, action: PayloadAction<ProviderBrandingResponse>) {
            state = action.payload;
        }
    }
});

const {addProviderBranding} = providerBrandingSlice.actions;
export const providerBrandingReducer = providerBrandingSlice.reducer;