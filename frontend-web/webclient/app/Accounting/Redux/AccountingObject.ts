import { LoadableContent, emptyLoadableContent } from "LoadableContent";
import { Dictionary, Page } from "Types";
import * as API from "../api";

export interface ResourceState {
    chart: LoadableContent<API.ChartResponse>
    usage: LoadableContent<API.Usage>
    events: LoadableContent<Page<API.AccountingEvent>>
}

export interface Type {
    resources: Dictionary<ResourceState>
}

export interface Wrapper {
    accounting: Type
}

export const emptyResourceState: () => ResourceState = () => ({
    chart: emptyLoadableContent(),
    usage: emptyLoadableContent(),
    events: emptyLoadableContent()
});

export const resourceName = (resource: string, subResource: string): string => `${resource}/${subResource}`;

export const init = (): Wrapper => ({
    accounting: {
        resources: {
            "storage/bytesUsed": emptyResourceState(),
            "compute/timeUsed": emptyResourceState()
        }
    }
});