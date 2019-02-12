import { Cloud } from "Authentication/SDUCloudObject";
import { favoritesQuery } from "Utilities/FileUtilities";
import { Page, PayloadAction, Error, SetLoadingAction } from "Types";
import { File } from "Files";
import { errorMessageOrDefault } from "UtilityFunctions";
import { RECEIVE_FAVORITES, SET_ERROR_MESSAGE, SET_FAVORITES_LOADING } from "./FavoritesReducer";

export type FavoriteActions = ReceiveFavorites | SetLoading | SetError;

export const fetchFavorites = async (pageNumber: number, itemsPerPage: number): Promise<ReceiveFavorites |
    SetError> => {
    try {
        const res = await Cloud.get<Page<File>>(favoritesQuery(pageNumber, itemsPerPage));
        return receiveFavorites(res.response);
    } catch (e) {
        return setErrorMessage(errorMessageOrDefault(e, "An error occurred"));
    }
}


type ReceiveFavorites = PayloadAction<typeof RECEIVE_FAVORITES, { page: Page<File> }>
const receiveFavorites = (page: Page<File>): ReceiveFavorites => ({
    type: RECEIVE_FAVORITES,
    payload: { page }
});

type SetError = Error<typeof SET_ERROR_MESSAGE>
const setErrorMessage = (error?: string): SetError => ({
    type: SET_ERROR_MESSAGE,
    payload: { error }
});

type SetLoading = SetLoadingAction<typeof SET_FAVORITES_LOADING>
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_FAVORITES_LOADING,
    payload: { loading }
});