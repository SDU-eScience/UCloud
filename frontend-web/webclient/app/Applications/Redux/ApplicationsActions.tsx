import { Cloud } from "Authentication/SDUCloudObject";
import {
    RECEIVE_APPLICATIONS,
    SET_APPLICATIONS_LOADING,
    APPLICATIONS_ERROR,
    RECEIVE_FAVORITE_APPLICATIONS,
    SET_FAVORITE_APPLICATIONS_LOADING
} from "./ApplicationsReducer";
import {
    SetLoadingAction,
    Page,
    Error,
    ReceivePage,
    PayloadAction
} from "Types";
import { Application } from ".."
import { hpcApplicationsQuery, hpcFavorites } from "Utilities/ApplicationUtilities";

export type ApplicationActions = ReceiveApplicationsAction | Error<typeof APPLICATIONS_ERROR> |
    SetLoadingAction<typeof SET_APPLICATIONS_LOADING> | ReceiveFavoritesAction | FavoritesLoading;

interface ReceiveApplicationsAction extends ReceivePage<typeof RECEIVE_APPLICATIONS, Application> { }
export const receiveApplications = (page: Page<Application>): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    payload: { page }
});

type ReceiveFavoritesAction = PayloadAction<typeof RECEIVE_FAVORITE_APPLICATIONS, { favorites: Page<Application> }>
export const receiveFavoriteApplications = (favorites: Page<Application>): ReceiveFavoritesAction => ({
    type: RECEIVE_FAVORITE_APPLICATIONS,
    payload: { favorites }
});

export const setErrorMessage = (error?: string): Error<typeof APPLICATIONS_ERROR> => ({
    type: APPLICATIONS_ERROR,
    payload: { error }
});

export const fetchApplications = (page: number, itemsPerPage: number): Promise<ReceiveApplicationsAction | Error<typeof APPLICATIONS_ERROR>> =>
    Cloud.get<Page<Application>>(hpcApplicationsQuery(page, itemsPerPage)).then(({ response }: { response: Page<Application> }) =>
        receiveApplications(response)
    ).catch(() => setErrorMessage("An error occurred while retrieving applications."));

export const fetchFavoriteApplications = (page: number, itemsPerPage: number): Promise<ReceiveFavoritesAction | Error<typeof APPLICATIONS_ERROR>> =>
    Cloud.get<Page<Application>>(hpcFavorites(itemsPerPage, page)).then(({ response }) =>
        receiveFavoriteApplications(response)
    ).catch(() => setErrorMessage("An error occurred while retrieving applications."));

/**
 * Sets the loading state for the applications component
 * @param {boolean} loading loading state for the applications component
 */
export const setLoading = (loading: boolean): SetLoadingAction<typeof SET_APPLICATIONS_LOADING> => ({
    type: SET_APPLICATIONS_LOADING,
    payload: { loading }
});

type FavoritesLoading = PayloadAction<typeof SET_FAVORITE_APPLICATIONS_LOADING, { favoritesLoading: boolean }>
export const setFavoritesLoading = (favoritesLoading: boolean): FavoritesLoading => ({
    type: SET_FAVORITE_APPLICATIONS_LOADING,
    payload: { favoritesLoading }
});