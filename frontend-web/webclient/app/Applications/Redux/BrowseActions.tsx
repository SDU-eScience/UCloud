import {FullAppInfo, WithAppMetadata} from "Applications";
import {Cloud} from "Authentication/SDUCloudObject";
import {LoadableEvent, LoadableEventTag, unwrapCall} from "LoadableContent";
import {Page, PayloadAction} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export enum Tag {
    RECEIVE_APP = "BROWSE_APP_RECEIVE_APP",
    RECEIVE_APPS_BY_KEY = "RECEIVE_APPS_BY_KEY"
}

export type Type = ReceiveApp | ReceiveAppsByKey;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Page<FullAppInfo>>>;
type ReceiveAppsByKey = PayloadAction<typeof Tag.RECEIVE_APPS_BY_KEY, {page: Page<FullAppInfo>, key: string}>

export const receivePage = (page: Page<FullAppInfo>): ReceiveApp => ({
    type: Tag.RECEIVE_APP,
    payload: {
        type: LoadableEventTag.CONTENT,
        content: page
    }
});

export const fetchByTag = async (tag: string, itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Page<FullAppInfo>>(buildQueryString(
            "/hpc/apps/searchTags",
            {
                query: tag,
                itemsPerPage,
                page
            }
        ))
    )
});

export const fetch = async (itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Page<FullAppInfo>>(buildQueryString(
            "/hpc/apps",
            {
                itemsPerPage,
                page
            }
        ))
    )
});

export async function receiveAppsByKey(itemsPerPage: number, page: number, tag: string): Promise<ReceiveAppsByKey> { 
    return ({
        type: Tag.RECEIVE_APPS_BY_KEY,
        payload: {
            page: (await Cloud.get<Page<FullAppInfo>>(buildQueryString(
                "/hpc/apps/searchTags",
                {
                    query: tag,
                    itemsPerPage,
                    page
                }
            ))).response,
            key: tag
    }})
}