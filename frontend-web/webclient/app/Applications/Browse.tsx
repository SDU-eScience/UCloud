import {Client} from "Authentication/HttpClientInstance";
import {ReduxObject} from "DefaultObjects";
import {loadingEvent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page} from "Types";
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

class Applications extends React.Component<ApplicationsProps> {
    public componentDidMount(): void {
        const {props} = this;
        props.onInit();

        this.fetch();
        props.setRefresh(() => this.fetch());
    }

    public componentDidUpdate(prevProps: ApplicationsProps): void {
        if (prevProps.location !== this.props.location) {
            this.fetch();
        }
    }

    public componentWillUnmount(): void {
        this.props.setRefresh();
    }

    public render(): JSX.Element {
        const main = (
            <Pagination.List
                loading={this.props.applicationsPage.loading}
                pageRenderer={this.renderPage}
                page={this.props.applicationsPage.content as Page<FullAppInfo>}
                onPageChanged={pageNumber => this.props.history.push(this.updatePage(pageNumber))}
            />
        );

        return (
            <LoadableMainContainer
                header={(
                    <Spacer
                        left={(<Heading.h1>{getQueryParam(this.props, "tag")}</Heading.h1>)}
                        right={(
                            <Pagination.EntriesPerPageSelector
                                content="Apps per page"
                                entriesPerPage={this.itemsPerPage()}
                                onChange={itemsPerPage => this.props.history.push(this.updateItemsPerPage(itemsPerPage))}
                            />
                        )}
                    />
                )}
                loadable={this.props.applicationsPage}
                main={main}
            />
        );
    }

    private renderPage = (page: Page<FullAppInfo>): JSX.Element => (<ApplicationPage onFavorite={this.onFavorite} page={page} />);

    private onFavorite = async (name: string, version: string): Promise<void> => {
        const page = this.props.applicationsPage.content as Page<FullAppInfo>;
        this.props.receiveApplications(await favoriteApplicationFromPage({
            client: Client,
            name,
            version,
            page
        }));
    };

    private pageNumber(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "page", "0"), 10);
    }

    private itemsPerPage(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "itemsPerPage", "25"), 10);
    }

    private tag(props: ApplicationsProps = this.props): string | null {
        return getQueryParam(props, "tag");
    }

    private updateItemsPerPage(newItemsPerPage: number): string {
        const tag = this.tag();
        if (tag === null) {
            return Pages.browse(newItemsPerPage, this.pageNumber());
        } else {
            return Pages.browseByTag(tag, newItemsPerPage, this.pageNumber());
        }
    }

    private updatePage(newPage: number): string {
        const tag = this.tag();
        if (tag === null) {
            return Pages.browse(this.itemsPerPage(), newPage);
        } else {
            return Pages.browseByTag(tag, this.itemsPerPage(), newPage);
        }
    }

    private fetch(): void {
        const itemsPerPage = this.itemsPerPage(this.props);
        const pageNumber = this.pageNumber(this.props);
        const tag = this.tag(this.props);

        if (tag === null) {
            this.props.fetchDefault(itemsPerPage, pageNumber);
        } else {
            this.props.fetchByTag(tag, itemsPerPage, pageNumber);
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
