import {Client} from "Authentication/HttpClientInstance";
import {loadingEvent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {favoriteApplicationFromPage} from "Utilities/ApplicationUtilities";
import {getQueryParam, getQueryParamOrElse, RouterLocationProps} from "Utilities/URIUtilities";
import {FullAppInfo} from ".";
import {ApplicationPage} from "./Installed";
import * as Pages from "./Pages";
import * as Actions from "./Redux/BrowseActions";
import {Type as ReduxType} from "./Redux/BrowseObject";

export interface ApplicationsOperations {
    onInit: () => void;
    fetchDefault: (itemsPerPage: number, page: number) => void;
    fetchByTag: (tag: string, itemsPerPage: number, page: number) => void;
    receiveApplications: (page: Page<FullAppInfo>) => void;
    setRefresh: (refresh?: () => void) => void;
}

export type ApplicationsProps = ReduxType & ApplicationsOperations & RouterLocationProps;

function Applications(props: ApplicationsProps): JSX.Element {
    React.useEffect(() => {
        props.onInit();

        fetch();
        props.setRefresh(() => fetch());
        return () => props.setRefresh();
    }, []);

    React.useEffect(() => {
        fetch();
    }, [props.location]);

    const main = (
        <Pagination.List
            loading={props.applicationsPage.loading}
            pageRenderer={renderPage}
            page={props.applicationsPage.content as Page<FullAppInfo>}
            onPageChanged={pageNumber => props.history.push(updatePage(pageNumber))}
        />
    );

    return (
        <LoadableMainContainer
            header={(
                <Spacer
                    left={(<Heading.h1>{getQueryParam(props, "tag")}</Heading.h1>)}
                    right={(
                        <Pagination.EntriesPerPageSelector
                            content="Apps per page"
                            entriesPerPage={getItemsPerPage()}
                            onChange={itemsPerPage => props.history.push(updateItemsPerPage(itemsPerPage))}
                        />
                    )}
                />
            )}
            loadable={props.applicationsPage}
            main={main}
        />
    );

    function renderPage(page: Page<FullAppInfo>): JSX.Element {
        return (<ApplicationPage onFavorite={onFavorite} page={page} />);
    }

    async function onFavorite(name: string, version: string): Promise<void> {
        const page = props.applicationsPage.content as Page<FullAppInfo>;
        props.receiveApplications(await favoriteApplicationFromPage({
            client: Client,
            name,
            version,
            page
        }));
    }

    function getPageNumber(argProps: ApplicationsProps = props): number {
        return parseInt(getQueryParamOrElse(argProps, "page", "0"), 10);
    }

    function getItemsPerPage(argProps: ApplicationsProps = props): number {
        return parseInt(getQueryParamOrElse(argProps, "itemsPerPage", "25"), 10);
    }

    function getTag(argProps: ApplicationsProps = props): string | null {
        return getQueryParam(argProps, "tag");
    }

    function updateItemsPerPage(newItemsPerPage: number): string {
        const tag = getTag();
        if (tag === null) {
            return Pages.browse(newItemsPerPage, getPageNumber());
        } else {
            return Pages.browseByTag(tag, newItemsPerPage, getPageNumber());
        }
    }

    function updatePage(newPage: number): string {
        const tag = getTag();
        if (tag === null) {
            return Pages.browse(getItemsPerPage(), newPage);
        } else {
            return Pages.browseByTag(tag, getItemsPerPage(), newPage);
        }
    }

    function fetch(): void {
        const itemsPerPage = getItemsPerPage(props);
        const pageNumber = getPageNumber(props);
        const tag = getTag(props);

        if (tag === null) {
            props.fetchDefault(itemsPerPage, pageNumber);
        } else {
            props.fetchByTag(tag, itemsPerPage, pageNumber);
        }
    }
}

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>
): ApplicationsOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    fetchByTag: async (tag: string, itemsPerPage: number, page: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetchByTag(tag, itemsPerPage, page));
    },

    fetchDefault: async (itemsPerPage: number, page: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetch(itemsPerPage, page));
    },

    receiveApplications: page => dispatch(Actions.receivePage(page)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({applicationsBrowse}: ReduxObject): ReduxType & {favCount: number} => ({
    ...applicationsBrowse,
    favCount: applicationsBrowse.applicationsPage.content ?
        applicationsBrowse.applicationsPage.content.items.filter(it => it.favorite).length : 0
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
