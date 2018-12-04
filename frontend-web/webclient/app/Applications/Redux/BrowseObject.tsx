import { LoadableContent, emptyLoadableContent } from "LoadableContent";
import { Page } from "Types";
import { Application } from "Applications";

export interface Type {
    applications: LoadableContent<Page<Application>>
}

export interface Wrapper {
    applicationsBrowse: Type
}

export const init = (): Wrapper => ({
    applicationsBrowse: {
        applications: emptyLoadableContent()
    }
});