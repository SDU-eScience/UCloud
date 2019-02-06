import { Cloud } from "Authentication/SDUCloudObject";
import { PayloadAction, Page } from "Types";
import { Application, ApplicationMetadata, WithAppMetadata } from "Applications";
import { LoadableEvent, unwrapCall } from "LoadableContent";
import { buildQueryString } from "Utilities/URIUtilities";

export enum Tag {
    RECEIVE_APP = "BROWSE_APP_RECEIVE_APP"
}

export type Type = ReceiveApp;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Page<WithAppMetadata>>>;

export const fetchByTag = async (tag: string, itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Page<WithAppMetadata>>(buildQueryString(
            "/hpc/apps/searchTags",
            {
                query: tag,
                itemsPerPage,
                page
            }
        ))
    )
})

export const fetch = async (itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Page<WithAppMetadata>>(buildQueryString(
            "/hpc/apps",
            {
                itemsPerPage,
                page
            }
        ))
    )
});