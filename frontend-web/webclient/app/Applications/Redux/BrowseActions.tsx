import {FullAppInfo} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {LoadableEvent, LoadableEventTag, unwrapCall} from "LoadableContent";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Error} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";

export enum Tag {
    RECEIVE_APP = "BROWSE_APP_RECEIVE_APP",
    RECEIVE_APPS_BY_KEY = "RECEIVE_APPS_BY_KEY",
    RECEIVE_APPS_BY_KEY_ERROR = "RECEIVE_APPS_BY_KEY_ERROR"
}

export type Type = ReceiveApp | ReceiveAppsByKey | ByTagError;

type ByTagError = Error<typeof Tag.RECEIVE_APPS_BY_KEY_ERROR>;
type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Page<FullAppInfo>>>;
type ReceiveAppsByKey = PayloadAction<typeof Tag.RECEIVE_APPS_BY_KEY, {page: Page<FullAppInfo>, key: string}>;

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
        Client.get<Page<FullAppInfo>>(buildQueryString(
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
        Client.get<Page<FullAppInfo>>(buildQueryString(
            "/hpc/apps",
            {
                itemsPerPage,
                page
            }
        ))
    )
});

export async function receiveAppsByKey(
    itemsPerPage: number,
    page: number,
    tag: string
): Promise<ReceiveAppsByKey | ByTagError> {
    try {
        return ({
            type: Tag.RECEIVE_APPS_BY_KEY,
            payload: {
                page: (await Client.get<Page<FullAppInfo>>(buildQueryString(
                    "/hpc/apps/searchTags",
                    {
                        query: tag,
                        itemsPerPage,
                        page
                    }
                ))).response,
                key: tag
            }
        });
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, `Could not fetch apps by tag ${tag}`), false);
        return {
            type: Tag.RECEIVE_APPS_BY_KEY_ERROR,
            payload: {}
        };
    }
}
