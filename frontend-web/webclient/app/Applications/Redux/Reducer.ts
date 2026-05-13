import {deepCopy} from "@/Utilities/CollectionUtilities";
import {ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";

import {Application} from "@/Applications/AppStoreApi";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";

export interface SidebarStateProps {
    favorites: ApplicationSummaryWithFavorite[];
    theme: "light" | "dark";
}

function initialState(): SidebarStateProps {
    return {
        favorites: [],
        theme: "light"
    }
}

const sidebarSlice = createSlice({
    name: "sidebar",
    initialState: initialState(),
    reducers: {
        setAppFavorites(state, action: PayloadAction<Application[]>) {
            state.favorites = action.payload;
        },
        toggleAppFavorite(state, action: PayloadAction<{app: Application, favorite: boolean}>) {
            const copy = deepCopy(state);
            const {app: metadata} = action.payload;
            if (action.payload.favorite) {
                copy.favorites.push(metadata);
                copy.favorites.sort((a, b) => a.metadata.name.localeCompare(b.metadata.name));
            } else {
                copy.favorites = state.favorites.filter(it => it.metadata.name !== metadata.metadata.name || it.metadata.version !== metadata.metadata.version);
            }
            state = copy;
        },
        toggleThemeRedux(state, action: PayloadAction<"light" | "dark">) {
            state.theme = action.payload;
        }
    }
});

export const {setAppFavorites, toggleAppFavorite, toggleThemeRedux} = sidebarSlice.actions;
export const sidebarReducer = sidebarSlice.reducer;
