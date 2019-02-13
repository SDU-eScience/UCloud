import { File } from "Files";
import { emptyPage, ComponentWithPage } from "DefaultObjects";
import { FavoriteType } from "Favorites/Favorites";

export interface Type extends ComponentWithPage<File> {
    shown: FavoriteType
}

export interface Wrapper {
    favorites: Type
}

export const init = (): Wrapper => ({
    favorites: {
        page: emptyPage,
        loading: false,
        error: undefined,
        shown: FavoriteType.FILES
    }
})