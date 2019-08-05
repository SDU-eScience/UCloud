import {SetLoadingAction} from "Types";
import {SET_FAVORITES_LOADING} from "./FavoritesReducer";

export type FavoriteActions = SetLoading;

type SetLoading = SetLoadingAction<typeof SET_FAVORITES_LOADING>
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_FAVORITES_LOADING,
    payload: {loading}
});

