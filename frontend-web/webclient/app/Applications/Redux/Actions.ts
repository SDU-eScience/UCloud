import {Application} from "@/Applications/AppStoreApi";
import {PayloadAction} from "@reduxjs/toolkit";

export type SidebarActionType = SetAppFavorites | ToggleFavorite | ToggleTheme;

export const SET_APP_FAVORITES = "SET_APP_FAVORITES";
type SetAppFavorites = PayloadAction<{favorites: Application[]}, typeof SET_APP_FAVORITES>;

export function setAppFavorites(favorites: Application[]): SetAppFavorites {
    return {
        type: SET_APP_FAVORITES,
        payload: {favorites}
    }
}

export const TOGGLE_APP_FAVORITE = "TOGGLE_APP_FAVORITE";
type ToggleFavorite = PayloadAction<{app: Application, favorite: boolean}, typeof TOGGLE_APP_FAVORITE>;

export function toggleAppFavorite(app: Application, favorite: boolean): ToggleFavorite {
    return {
        type: TOGGLE_APP_FAVORITE,
        payload: {
            app,
            favorite
        }
    };
}

export const TOGGLE_THEME_REDUX = "TOGGLE_THEME";
type ToggleTheme = PayloadAction<"light" | "dark", typeof TOGGLE_THEME_REDUX>
export function toggleThemeRedux(theme: "light" | "dark"): ToggleTheme {
    return {
        type: TOGGLE_THEME_REDUX,
        payload: theme
    };
}