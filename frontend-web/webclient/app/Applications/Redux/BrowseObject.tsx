import { LoadableContent, emptyLoadableContent } from "LoadableContent";
import { Page } from "Types";
import { ApplicationMetadata, WithAppMetadata } from "Applications";

export interface Type {
    applications: LoadableContent<Page<WithAppMetadata>>
}

export interface Wrapper {
    applicationsBrowse: Type
}

export const init = (): Wrapper => ({
    applicationsBrowse: {
        applications: emptyLoadableContent()
    }
});