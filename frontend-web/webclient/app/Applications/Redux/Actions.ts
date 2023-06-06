import {compute} from "@/UCloud";

export type SidebarActionType = SetAppFavorites | ToggleFavorite | ToggleTheme;

export const SET_APP_FAVORITES = "SET_APP_FAVORITES";
type SetAppFavorites = PayloadAction<typeof SET_APP_FAVORITES, {favorites: compute.ApplicationSummaryWithFavorite[]}>;

export function setAppFavorites(favorites: compute.ApplicationSummaryWithFavorite[]): SetAppFavorites {
    return {
        type: SET_APP_FAVORITES,
        payload: {favorites}
    }
}

export const TOGGLE_APP_FAVORITE = "TOGGLE_APP_FAVORITE";
type ToggleFavorite = PayloadAction<typeof TOGGLE_APP_FAVORITE, {app: compute.ApplicationSummaryWithFavorite, favorite: boolean}>;

export function toggleAppFavorite(app: compute.ApplicationSummaryWithFavorite, favorite: boolean): ToggleFavorite {
    return {
        type: TOGGLE_APP_FAVORITE,
        payload: {
            app,
            favorite
        }
    };
}

export const TOGGLE_THEME_REDUX = "TOGGLE_THEME";
type ToggleTheme = PayloadAction<typeof TOGGLE_THEME_REDUX, "light" | "dark">
export function toggleThemeRedux(theme: "light" | "dark"): ToggleTheme {
    return {
        type: TOGGLE_THEME_REDUX,
        payload: theme
    };
}