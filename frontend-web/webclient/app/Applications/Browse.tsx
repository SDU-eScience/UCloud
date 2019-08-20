import {Cloud} from "Authentication/SDUCloudObject";
import {ReduxObject} from "DefaultObjects";
import {loadingEvent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Page} from "Types";
import {Link} from "ui-components";
import {GridCardGroup} from "ui-components/Grid";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {favoriteApplicationFromPage} from "Utilities/ApplicationUtilities";
import {getQueryParam, getQueryParamOrElse, RouterLocationProps} from "Utilities/URIUtilities";
import {FullAppInfo, WithAppMetadata} from ".";
import {ApplicationCard} from "./Card";
import * as Pages from "./Pages";
import * as Actions from "./Redux/BrowseActions";
import {Type as ReduxType} from "./Redux/BrowseObject";

const CategoryList = styled.ul`
    padding: 0;

    & > li {
        list-style: none;
    }
`;

const CategoryItem: React.FunctionComponent<{tag?: string}> = props => (
    <li><Link to={!!props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link></li>
);

const Sidebar: React.FunctionComponent<{itemsPerPage: number}> = ({itemsPerPage}) => (<>
    <Heading.h4 m="0 0 14px"><Link to={Pages.browse(itemsPerPage)}>All</Link></Heading.h4>

    <Heading.h4 m="0 0 -14px">Categories</Heading.h4>
    <CategoryList>
        <CategoryItem tag="Bioinformatics">Bioinformatics</CategoryItem>
        <CategoryItem tag="Natural Science">Natural Sciences</CategoryItem>
        <CategoryItem tag="Toy">Toys</CategoryItem>
    </CategoryList>

    <Heading.h4 m="0 0 -14px">Tools</Heading.h4>
    <CategoryList>
        <CategoryItem tag="Cell Ranger">Cell Ranger</CategoryItem>
        <CategoryItem tag="HOMER">HOMER</CategoryItem>
        <CategoryItem tag="Kallisto">Kallisto</CategoryItem>
        <CategoryItem tag="MACS2">MACS2</CategoryItem>
        <CategoryItem tag="Salmon">Salmon</CategoryItem>
        <CategoryItem tag="SAMtools">SAMtools</CategoryItem>
    </CategoryList>
</>);

export interface ApplicationsOperations {
    onInit: () => void;
    fetchDefault: (itemsPerPage: number, page: number) => void;
    fetchByTag: (tag: string, itemsPerPage: number, page: number) => void;
    receiveApplications: (page: Page<WithAppMetadata>) => void;
    setRefresh: (refresh?: () => void) => void;
}

export type ApplicationsProps = ReduxType & ApplicationsOperations & RouterLocationProps;

class Applications extends React.Component<ApplicationsProps> {
    public componentDidMount() {
        const {props} = this;
        props.onInit();

        this.fetch();
        props.setRefresh(() => this.fetch());
    }

    public componentDidUpdate(prevProps: ApplicationsProps) {
        if (prevProps.location !== this.props.location) {
            this.fetch();
        }
    }

    public componentWillUnmount() {
        this.props.setRefresh();
    }

    public render() {
        const main = (
            <Pagination.List
                loading={this.props.applications.loading}
                pageRenderer={(page: Page<FullAppInfo>) =>
                    <GridCardGroup>
                        {page.items.map((app, index) =>
                            <ApplicationCard
                                key={index}
                                onFavorite={async () =>
                                    this.props.receiveApplications(await favoriteApplicationFromPage({
                                        name: app.metadata.name,
                                        version: app.metadata.version, page, cloud: Cloud,
                                    }))
                                }
                                app={app}
                                isFavorite={app.favorite}
                                tags={app.tags}
                            />
                        )}
                    </GridCardGroup>
                }
                page={this.props.applications.content as Page<WithAppMetadata>}
                onPageChanged={pageNumber => this.props.history.push(this.updatePage(pageNumber))}
            />
        );

        return (
            <LoadableMainContainer
                header={<Spacer left={<Heading.h1>Applications</Heading.h1>} right={
                    <Pagination.EntriesPerPageSelector
                        content="Apps per page"
                        entriesPerPage={this.itemsPerPage()}
                        onChange={itemsPerPage => this.props.history.push(this.updateItemsPerPage(itemsPerPage))}
                    />
                } />}
                loadable={this.props.applications}
                main={main}
                fallbackSidebar={<Sidebar itemsPerPage={this.itemsPerPage()} />}
                sidebar={<Sidebar itemsPerPage={this.itemsPerPage()} />}
            />
        );
    }

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

    private fetch() {
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
    favCount: applicationsBrowse.applications.content ?
        applicationsBrowse.applications.content.items.filter((it: any) => it.favorite).length : 0
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
