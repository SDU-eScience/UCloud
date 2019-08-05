import {File} from "Files";
import {emptyPage, ComponentWithPage} from "DefaultObjects";

export interface Type extends ComponentWithPage<File> {
}

export interface Wrapper {
    favorites: Type
}

export const init = (): Wrapper => ({
    favorites: {
        page: emptyPage,
        loading: false,
        error: undefined,
    }
});