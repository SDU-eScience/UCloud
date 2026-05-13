import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {brandingApi, BrandingResponse, BrandingLoginPageType} from "@/UCloud/BrandingApi";
import {useDispatch} from "react-redux";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";

export const AutomaticBranding: React.FunctionComponent = () => {
    const [branding, fetchBranding] = useCloudAPI<BrandingResponse>(
        brandingApi.retrieve(),
        {
            deploymentName: "",
            loginPage: {type: BrandingLoginPageType.DEIC, primaryLogoUrl: "", secondaryLogoUrls: []},
        },
    );

    React.useEffect(() => {
        const intervalId = setInterval(() => {
            fetchBranding(brandingApi.retrieve());
        }, 1000 * 60 * 60);
        return () => {
            clearInterval(intervalId);
        };
    }, []);
    const dispatch = useDispatch();

    React.useEffect(() => {
        dispatch(addBranding(branding.data));
    }, [branding.data]);

    return null;
};

export function initBranding(): BrandingResponse {
    return {
        deploymentName: "",
        loginPage: {type: BrandingLoginPageType.DEIC, primaryLogoUrl: "", secondaryLogoUrls: []},
    };
}

const brandingSlice = createSlice({
    name: "branding",
    initialState: initBranding(),
    reducers: {
        addBranding(state, action: PayloadAction<BrandingResponse>) {
            state = action.payload;
        }
    }
})

export const {addBranding} = brandingSlice.actions;
export const brandingReducer = brandingSlice.reducer;