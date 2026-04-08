import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {brandingApi, BrandingResponse} from "@/UCloud/BrandingApi";
import {useDispatch} from "react-redux";
import {PayloadAction} from "@reduxjs/toolkit";

export const AutomaticBranding: React.FunctionComponent = () => {
  const [branding, fetchBranding] = useCloudAPI<BrandingResponse>(
    brandingApi.retrieve(),
    {
      deploymentName: "",
      loginPage: { type: 1, primaryLogoUrl: "", secondaryLogoUrls: [] },
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
    dispatch({ type: ADD_BRANDING, payload: branding.data });
  }, [branding.data]);

  return null;
};

const ADD_BRANDING = "ADD_BRANDING";
type SetBranding = PayloadAction<BrandingResponse, typeof ADD_BRANDING>;

type BrandingAction = SetBranding;

export function initBranding(): BrandingResponse {
  return {
    deploymentName: "",
    loginPage: { type: 1, primaryLogoUrl: "", secondaryLogoUrls: [] },
  };
}

export function brandingReducer(
  state: BrandingResponse = initBranding(),
  action: BrandingAction,
): BrandingResponse {
  switch (action.type) {
    case ADD_BRANDING: {
      return action.payload;
    }
    default:
      return state;
  }
}
