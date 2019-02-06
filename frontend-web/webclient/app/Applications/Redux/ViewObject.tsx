import { WithAppMetadata, WithAppFavorite, WithAppInvocation } from "Applications";
import { Page } from "Types";
import { emptyLoadableContent, LoadableContent } from "LoadableContent";

export interface Type {
    application: LoadableContent<WithAppMetadata & WithAppFavorite & WithAppInvocation>
    previous: LoadableContent<Page<WithAppMetadata>>
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