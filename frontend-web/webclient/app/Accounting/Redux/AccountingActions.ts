import {Client} from "Authentication/HttpClientInstance";
import {LoadableEvent, unwrapCall} from "LoadableContent";
import {PayloadAction} from "Types";
import * as API from "../api";
import {resourceName} from "./AccountingObject";

export enum Tag {
    RECEIVE_USAGE = "ACCOUNTING_RECEIVE_USAGE",
    CLEAR_RESOURCE = "ACCOUNTING_CLEAR_RESOURCE"
}

export type Type = ReceiveUsage | ClearResource;
type ReceiveUsage = PayloadAction<typeof Tag.RECEIVE_USAGE, {resource: string, event: LoadableEvent<API.Usage>}>;
type ClearResource = PayloadAction<typeof Tag.CLEAR_RESOURCE, {resource: string}>;

export const fetchUsage = async (resource: string, subResource: string): Promise<ReceiveUsage> => ({
    type: Tag.RECEIVE_USAGE,
    payload: {
        resource: resourceName(resource, subResource),
        event: await unwrapCall(
            Client.get(`/accounting/${resource}/${subResource}/usage`)
        )
    }
});

export const clearResource = (resource: string, subResource: string): ClearResource => ({
    type: Tag.CLEAR_RESOURCE,
    payload: {
        resource: resourceName(resource, subResource)
    }
});
