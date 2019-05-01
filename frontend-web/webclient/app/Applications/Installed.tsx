import * as React from "react";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { updatePageTitle, StatusActions } from "Navigation/Redux/StatusActions";
import { setPrioritizedSearch, HeaderActions, setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { WithAppMetadata, WithAppFavorite } from "Applications";
import { Page } from "Types";
import * as Pagination from "Pagination";
import { ApplicationCard } from "./Card";
import { LoadingMainContainer } from "MainContainer/MainContainer";
import { GridCardGroup } from "ui-components/Grid";
import * as Actions from "./Redux/FavoriteActions";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { loadingEvent } from "LoadableContent";
import { Box } from "ui-components";
import { Spacer } from "ui-components/Spacer";

interface InstalledOperations {
    onInit: () => void
    fetchItems: (pageNumber: number, itemsPerPage: number) => void
    setRefresh: (refresh?: () => void) => void
}

type InstalledStateProps = ReduxType;

type InstalledProps = InstalledOperations & InstalledStateProps;

class Installed extends React.Component<InstalledProps & { header: any }> {
    componentDidMount() {
        const { props } = this;

        props.onInit();
        props.fetchItems(0, 25);
        const { content } = props.applications;
        const pageNumber = !!content ? content.pageNumber : 0;
        const itemsPerPage = !!content ? content.itemsPerPage : 25;

        props.setRefresh(() => props.fetchItems(pageNumber, itemsPerPage))

    }

    // FIXME, should be replaced;
    componentWillReceiveProps(nextProps: InstalledProps) {
        const { content } = nextProps.applications;
        const pageNumber = !!content ? content.pageNumber : 0;
        const itemsPerPage = !!content ? content.itemsPerPage : 25;
        nextProps.setRefresh(() => this.props.fetchItems(pageNumber, itemsPerPage));
    }

    componentWillUnmount() {
        this.props.setRefresh();
    }

    render() {
        const { props } = this;
        const page = props.applications.content as Page<WithAppMetadata & WithAppFavorite>;
        const itemsPerPage = !!page ? page.itemsPerPage : 25;  
        const pageNumber = !!page ? page.pageNumber : 0;
        const main = (
            <>
                <Spacer left={null} right={props.applications.loading ? null : <Pagination.EntriesPerPageSelector
                    content="Apps per page"
                    entriesPerPage={itemsPerPage}
                    onChange={itemsPerPage => props.fetchItems(0, itemsPerPage)}
                />} />
                <Pagination.List
                    loading={props.applications.loading}
                    page={page}
                    onPageChanged={pageNumber => props.fetchItems(pageNumber, page.itemsPerPage)}
                    pageRenderer={page => <Box mt="5px"><InstalledPage page={page} /></Box>}
                />
            </>
        );
        return (
            <LoadingMainContainer
                header={props.header}
                loadable={this.props.applications}
                main={main}
                sidebar={null}
            />
        );
    }
}

const InstalledPage: React.StatelessComponent<{ page: Page<WithAppMetadata & WithAppFavorite> }> = props => (
    <GridCardGroup>
        {props.page.items.map((it, idx) => (
            <ApplicationCard app={it} key={idx} isFavorite={it.favorite} linkToRun />)
        )}
    </GridCardGroup>
);

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>): InstalledOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"))
        dispatch(setPrioritizedSearch("applications"))
    },

    fetchItems: async (pageNumber: number, itemsPerPage: number) => {
        dispatch({ type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true) });
        dispatch(await Actions.fetch(itemsPerPage, pageNumber))
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): InstalledStateProps => state.applicationsFavorite;

export default connect(mapStateToProps, mapDispatchToProps)(Installed);