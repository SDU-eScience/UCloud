import { Cloud } from "Authentication/SDUCloudObject";
import { PayloadAction, Page } from "Types";
import { WithAppMetadata, WithAppFavorite, WithAppInvocation } from "Applications";
import { LoadableEvent, unwrapCall } from "LoadableContent";
import { hpcFavoriteApp } from "Utilities/ApplicationUtilities";

export enum Tag {
    RECEIVE_APP = "VIEW_APP_RECEIVE_APP",
    RECEIVE_PREVIOUS = "VIEW_APP_RECEIVE_PREVIOUS",
    RECEIVE_FAVORITE = "VIEW_APP_RECEIVE_FAVORITE"
}

export type Type = ReceiveApp | ReceivePrevious | ReceiveFavorite;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<WithAppMetadata & WithAppFavorite & WithAppInvocation>>;
type ReceivePrevious = PayloadAction<typeof Tag.RECEIVE_PREVIOUS, LoadableEvent<Page<WithAppMetadata & WithAppFavorite & WithAppInvocation>>>;
type ReceiveFavorite = PayloadAction<typeof Tag.RECEIVE_FAVORITE, LoadableEvent<void>>;

export const fetchApplication = async (name: string, version: string): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<WithAppMetadata & WithAppFavorite & WithAppInvocation>(`/hpc/apps/${encodeURIComponent(name)}/${encodeURIComponent(version)}`)
    )
});

export const fetchPreviousVersions = async (name: string): Promise<ReceivePrevious> => ({
    type: Tag.RECEIVE_PREVIOUS,
    payload: await unwrapCall(
        Cloud.get<Page<WithAppMetadata & WithAppFavorite & WithAppInvocation>>(`/hpc/apps/${encodeURIComponent(name)}`)
    )
});


export const favoriteApplication = async (name: string, version: string): Promise<ReceiveFavorite> => ({
    type: Tag.RECEIVE_FAVORITE,
    payload: await unwrapCall(
        Cloud.post(hpcFavoriteApp(name, version))
    )
});