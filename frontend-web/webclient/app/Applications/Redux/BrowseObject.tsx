import {FullAppInfo, WithAppMetadata} from "Applications";
import {emptyLoadableContent, LoadableContent} from "LoadableContent";
import {Page} from "Types";

export interface Type {
    applications: Map<string, Page<WithAppMetadata>>;
    loading: boolean;
    applicationsPage: LoadableContent<Page<FullAppInfo>>;
}

export interface Wrapper {
    applicationsBrowse: Type;
}

export const init = (): Wrapper => ({
    applicationsBrowse: {
        applications: new Map(),
        loading: false,
        applicationsPage: emptyLoadableContent()
    }
});
