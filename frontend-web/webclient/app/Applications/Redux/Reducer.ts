import {compute} from "@/UCloud";
import {SET_APP_FAVORITES, TOGGLE_APP_FAVORITE, SidebarActionType} from "./Actions";
import {deepCopy} from "@/Utilities/CollectionUtilities";

export interface SidebarStateProps {
    favorites: compute.ApplicationSummaryWithFavorite[];
}

export const sidebar = (state: SidebarStateProps = {favorites: []}, action: SidebarActionType): SidebarStateProps => {
    switch (action.type) {
        case SET_APP_FAVORITES: {
            return action.payload;
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
        default: {
            return state;
        }
    }
};

export default sidebar;
