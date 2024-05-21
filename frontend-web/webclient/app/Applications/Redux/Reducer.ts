import {SET_APP_FAVORITES, TOGGLE_APP_FAVORITE, SidebarActionType, TOGGLE_THEME_REDUX} from "./Actions";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";

export interface SidebarStateProps {
    favorites: ApplicationSummaryWithFavorite[];
    theme: "light" | "dark";
}

export const sidebar = (state: SidebarStateProps = {favorites: [], theme: "light"}, action: SidebarActionType): SidebarStateProps => {
    switch (action.type) {
        case SET_APP_FAVORITES: {
            return {theme: state.theme, ...action.payload, };
        }
        case TOGGLE_APP_FAVORITE: {
            const copy = deepCopy(state);
            const {app: metadata} = action.payload;
            if (action.payload.favorite) {
                copy.favorites.push(metadata);
                copy.favorites.sort((a, b) => a.metadata.name.localeCompare(b.metadata.name));
            } else {
                copy.favorites = state.favorites.filter(it => it.metadata.name !== metadata.metadata.name || it.metadata.version !== metadata.metadata.version);
            }

            return copy;
        }

        case TOGGLE_THEME_REDUX: {
            return {
                ...state, theme: action.payload
            }
        }
        default: {
            return state;
        }
    }
};

export default sidebar;
