import {WithAppMetadata, FullAppInfo} from "Applications";
import { Page } from "Types";
import { emptyLoadableContent, LoadableContent } from "LoadableContent";

export interface Type {
    application: LoadableContent<FullAppInfo>
    previous: LoadableContent<Page<FullAppInfo>>
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