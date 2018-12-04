import { Application } from "Applications";
import { Page } from "Types";
import { emptyLoadableContent, LoadableContent } from "LoadableContent";

export interface Type {
    application: LoadableContent<Application>
    previous: LoadableContent<Page<Application>>
    favorite: LoadableContent<void>
}

export interface Wrapper {
    applicationView: Type
}

export function init(): Wrapper {
    return {
        applicationView: { 
            application: emptyLoadableContent(), 
            previous: emptyLoadableContent(),
            favorite: emptyLoadableContent()
        }
    };
}