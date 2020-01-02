import {FullAppInfo} from "Applications";
import {emptyLoadableContent, LoadableContent} from "LoadableContent";
import {Page} from "Types";

export interface Type {
    application: LoadableContent<FullAppInfo>;
    previous: LoadableContent<Page<FullAppInfo>>;
    favorite: LoadableContent<void>;
}

export interface Wrapper {
    applicationView: Type;
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