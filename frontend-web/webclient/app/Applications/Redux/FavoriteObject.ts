import {LoadableContent, emptyLoadableContent} from "LoadableContent";
import {WithAppMetadata, WithAppFavorite} from "Applications";

export interface Type {
    applications: LoadableContent<Page<WithAppMetadata & WithAppFavorite>>
}

export interface Wrapper {
    applicationsFavorite: Type
}

export const init = (): Wrapper => ({
    applicationsFavorite: {
        applications: emptyLoadableContent()
    }
});