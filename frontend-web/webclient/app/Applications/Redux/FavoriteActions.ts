import {WithAppFavorite, WithAppMetadata} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {LoadableEvent, unwrapCall} from "LoadableContent";
import {buildQueryString} from "Utilities/URIUtilities";

export enum Tag {
    RECEIVE_APP = "FAVORITE_APPS_RECEIVE_APP"
}

export type Type = ReceiveApp;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Page<WithAppMetadata & WithAppFavorite>>>;

export const fetch = async (itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Client.get<Page<WithAppMetadata & WithAppFavorite>>(buildQueryString(
            "/hpc/apps/favorites",
            {
                itemsPerPage,
                page
            }
        ))
    )
});
