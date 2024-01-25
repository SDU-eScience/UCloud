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
            const {app: metadata} = action.payload;
            if (action.payload.favorite) {
                state.favorites.push(metadata);
                state.favorites.sort((a, b) => a.metadata.name.localeCompare(b.metadata.name));
            } else {
                state.favorites = state.favorites.filter(it => it.metadata.name !== metadata.metadata.name || it.metadata.version !== metadata.metadata.version);
            }

            return deepCopy(state);
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
