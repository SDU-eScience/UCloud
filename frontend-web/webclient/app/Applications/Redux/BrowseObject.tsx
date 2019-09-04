import { LoadableContent, emptyLoadableContent } from "LoadableContent";
import { Page } from "Types";
import { ApplicationMetadata, WithAppMetadata } from "Applications";

export interface Type {
    applications: Map<string, Page<WithAppMetadata>>
    loading: boolean
}

export interface Wrapper {
    applicationsBrowse: Type
}

export const init = (): Wrapper => ({
    applicationsBrowse: {
        applications: new Map(),
        loading: false
    }
});