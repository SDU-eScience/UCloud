import {compute} from "@/UCloud";

export type SidebarActionType = SetAppFavorites | ToggleFavorite; 

export const SET_APP_FAVORITES = "SET_APP_FAVORITES";
type SetAppFavorites = PayloadAction<typeof SET_APP_FAVORITES, {favorites: compute.ApplicationMetadata[]}>;

export function setAppFavorites(favorites: compute.ApplicationMetadata[]): SetAppFavorites {
    return {
        type: SET_APP_FAVORITES,
        payload: {favorites}
    }
}

export const TOGGLE_APP_FAVORITE = "TOGGLE_APP_FAVORITE";
type ToggleFavorite = PayloadAction<typeof TOGGLE_APP_FAVORITE, {metadata: compute.ApplicationMetadata, favorite: boolean}>;

export function toggleAppFavorite(app: compute.ApplicationMetadata, favorite: boolean): ToggleFavorite {
    return {
        type: TOGGLE_APP_FAVORITE,
        payload: {
            metadata: app,
            favorite
        }
    };
}