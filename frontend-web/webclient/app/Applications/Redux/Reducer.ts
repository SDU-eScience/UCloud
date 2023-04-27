import {compute} from "@/UCloud";
import {SET_APP_FAVORITES, TOGGLE_APP_FAVORITE, SidebarActionType} from "./Actions";
import {deepCopy} from "@/Utilities/CollectionUtilities";

export interface SidebarStateProps {
    favorites: compute.ApplicationMetadata[];
}

export const sidebar = (state: SidebarStateProps = {favorites: []}, action: SidebarActionType): SidebarStateProps => {
    switch (action.type) {
        case SET_APP_FAVORITES: {
            return action.payload;
        }
        case TOGGLE_APP_FAVORITE: {
            const {metadata} = action.payload;
            if (action.payload.favorite) {
                state.favorites.push(metadata);
                state.favorites.sort((a, b) => a.name.localeCompare(b.name));
            } else {
                state.favorites = state.favorites.filter(it => it.name !== metadata.name && it.version !== metadata.version);
            }
            
            return deepCopy(state);
        }
        default: {
            return state;
        }
    }
};

export default sidebar;
