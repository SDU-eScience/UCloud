import * as React from "react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { updatePageTitle, StatusActions, setActivePage } from "Navigation/Redux/StatusActions";
import { Page } from "Types";
import { WithAppFavorite, WithAppMetadata } from ".";
import { setPrioritizedSearch, HeaderActions, setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { LoadingMainContainer } from "MainContainer/MainContainer";
import { ApplicationCard } from "./Card";
import styled from "styled-components";
import * as Heading from "ui-components/Heading";
import { Link } from "ui-components";
import { GridCardGroup } from "ui-components/Grid";
import { getQueryParam, RouterLocationProps, getQueryParamOrElse } from "Utilities/URIUtilities";
import * as Pages from "./Pages";
import { Type as ReduxType } from "./Redux/BrowseObject";
import * as Actions from "./Redux/BrowseActions";
import { loadingEvent } from "LoadableContent";
import { favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { SidebarPages } from "ui-components/Sidebar";

const CategoryList = styled.ul`
    padding: 0;

    & > li {
        list-style: none;
    }
`;

const CategoryItem: React.StatelessComponent<{ tag?: string }> = props => (
    <li><Link to={!!props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link></li>
);

const Sidebar: React.StatelessComponent = () => (<>
    <Heading.h4>Featured</Heading.h4>
    <CategoryList>
        <CategoryItem>Popular</CategoryItem>
    </CategoryList>

    <Heading.h4>Categories</Heading.h4>
    <CategoryList>
        <CategoryItem tag="Toy">Toys</CategoryItem>
    </CategoryList>

    <Heading.h4>Fields</Heading.h4>
    <CategoryList>
        <CategoryItem tag="Natural Science">Natural Sciences</CategoryItem>
        <CategoryItem tag="Formal Science">Formal Sciences</CategoryItem>
        <CategoryItem tag="Life Science">Life Sciences</CategoryItem>
        <CategoryItem tag="Social Science">Social Sciences</CategoryItem>
        <CategoryItem tag="Applied Science">Applied Sciences</CategoryItem>
        <CategoryItem tag="Interdisciplinary Sciencs">Interdisciplinary Sciences</CategoryItem>
        <CategoryItem tag="Philosophy Science">Philosophy Sciences</CategoryItem>
    </CategoryList>
</>);

export interface ApplicationsOperations {
    onInit: () => void
    fetchDefault: (itemsPerPage: number, page: number) => void
    fetchByTag: (tag: string, itemsPerPage: number, page: number) => void
    setActivePage: () => void
    setRefresh: (refresh?: () => void) => void
}

export type ApplicationsProps = ReduxType & ApplicationsOperations & RouterLocationProps;

class Applications extends React.Component<ApplicationsProps> {
    componentDidMount() {
        const { props } = this;
        props.onInit();

        this.fetch(props);
        props.setActivePage();
        props.setRefresh(() => this.fetch(props));
    }

    componentDidUpdate(prevProps: ApplicationsProps) {
        if (prevProps.location !== this.props.location) {
            this.fetch(this.props);
        }
    }

    componentWillReceiveProps(nextProps: ApplicationsProps) {
        this.props.setRefresh(() => this.fetch(nextProps));
    }

    componentWillUnmount() {
        this.props.setRefresh();
    }

    private pageNumber(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "page", "0"));
    }

    private itemsPerPage(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "itemsPerPage", "25"));
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

    updatePage(newPage: number): string {
        const tag = this.tag();
        if (tag === null) {
            return Pages.browse(this.itemsPerPage(), newPage);
        } else {
            return Pages.browseByTag(tag, this.itemsPerPage(), newPage);
        }
    }

    fetch(props: ApplicationsProps) {
        const itemsPerPage = this.itemsPerPage(props);
        const pageNumber = this.pageNumber(props);
        const tag = this.tag(props);

        if (tag === null) {
            props.fetchDefault(itemsPerPage, pageNumber);
        } else {
            props.fetchByTag(tag, itemsPerPage, pageNumber);
        }
    }

    render() {
        const main = (
            <Pagination.List
                loading={this.props.applications.loading}
                customEntriesPerPage
                pageRenderer={(page: Page<WithAppMetadata & WithAppFavorite>) =>
                    <GridCardGroup>
                        {page.items.map((app, index) =>
                            <ApplicationCard
                                key={index}
                                onFavorite={async () => {
                                    await favoriteApplicationFromPage(app.metadata.name, app.metadata.version, page, Cloud);
                                    this.fetch(this.props);
                                }}
                                app={app}
                                isFavorite={app.favorite}
                            />
                        )}
                    </GridCardGroup>
                }
                page={this.props.applications.content as Page<WithAppMetadata>}
                onPageChanged={pageNumber => this.props.history.push(this.updatePage(pageNumber))}
            />
        );

        return (
            <LoadingMainContainer
                header={<Heading.h1>Browse</Heading.h1>}
                loadable={this.props.applications}
                main={main}
                fallbackSidebar={<Sidebar />}
                sidebar={<Sidebar />}
            />
        );
    }
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>): ApplicationsOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"))
        dispatch(setPrioritizedSearch("applications"))
    },

    fetchByTag: async (tag: string, itemsPerPage: number, page: number) => {
        dispatch({ type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true) });
        dispatch(await Actions.fetchByTag(tag, itemsPerPage, page));
    },

    fetchDefault: async (itemsPerPage: number, page: number) => {
        dispatch({ type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true) });
        dispatch(await Actions.fetch(itemsPerPage, page));
    },

    setActivePage: () => dispatch(setActivePage(SidebarPages.AppStore)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): ReduxType => {
    return state.applicationsBrowse;
}

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
