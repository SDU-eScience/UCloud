import * as React from "react";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { updatePageTitle, StatusActions } from "Navigation/Redux/StatusActions";
import { setPrioritizedSearch, HeaderActions } from "Navigation/Redux/HeaderActions";
import { Application } from "Applications";
import { Page } from "Types";
import * as Pagination from "Pagination";
import { NewApplicationCard } from "./Card";
import { LoadingMainContainer } from "MainContainer/MainContainer";
import { CardGroup } from "ui-components/Card";
import * as Actions from "./Redux/FavoriteActions";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { loadingEvent } from "LoadableContent";

interface InstalledOperations {
    onInit: () => void
    fetchItems: (pageNumber: number, itemsPerPage: number) => void
}

type InstalledStateProps = ReduxType;

type InstalledProps = InstalledOperations & InstalledStateProps;

class Installed extends React.Component<InstalledProps> {
    componentDidMount() {
        const { props } = this;

        props.onInit();
        props.fetchItems(0, 25);
    }

    render() {
        const page = this.props.applications.content as Page<Application>;
        const main = (
            <Pagination.List
                onRefresh={() => this.props.fetchItems(page.pageNumber, page.itemsPerPage)}
                page={page}
                onItemsPerPageChanged={size => this.props.fetchItems(0, size)}
                onPageChanged={pageNumber => this.props.fetchItems(pageNumber, page.itemsPerPage)}
                pageRenderer={page => <InstalledPage page={page} />}
            />
        );

        return (
            <LoadingMainContainer
                loadable={this.props.applications}
                main={main}
                sidebar={null}
            />
        );
    }
}

const InstalledPage: React.StatelessComponent<{ page: Page<Application> }> = props => (
    <CardGroup>
        {props.page.items.map((it, idx) => (
            <NewApplicationCard app={it} key={idx} linkToRun />)
        )}
    </CardGroup>
);

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>): InstalledOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"))
        dispatch(setPrioritizedSearch("applications"))
    },

    fetchItems: async (pageNumber: number, itemsPerPage: number) => {
        dispatch({ type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true) });
        dispatch(await Actions.fetch(itemsPerPage, pageNumber))
    }
});

const mapStateToProps = (state: ReduxObject): InstalledStateProps => state.applicationsFavorite;

export default connect(mapStateToProps, mapDispatchToProps)(Installed);