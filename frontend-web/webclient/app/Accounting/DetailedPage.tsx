import * as React from "react";
import * as Actions from "./Redux/AccountingActions";
import { connect } from "react-redux";
import { ReduxObject, emptyPage } from "DefaultObjects";
import { Dispatch } from "redux";
import { match } from "react-router-dom";
import { LoadableContent } from "LoadableContent";
import * as API from "./api";
import { Page, ClearRefresh } from "Types";
import { resourceName, emptyResourceState } from "./Redux/AccountingObject";
import { LoadableMainContainer } from "MainContainer/MainContainer";
import { Chart, Breakdown } from "Accounting";
import * as Heading from "ui-components/Heading";
import { ContainerForText, Box } from "ui-components";
import * as Pagination from "Pagination";
import { setRefreshFunction, SetRefreshFunction } from "Navigation/Redux/HeaderActions";

type DetailedPageProps = OwnProps & StateProps & Operations;

interface OwnProps {
    match: match<{ resource: string, subResource: string }>
}

interface StateProps {
    events: LoadableContent<Page<API.AccountingEvent>>
    chart: LoadableContent<API.ChartResponse>
}

interface Operations extends ClearRefresh {
    refresh: () => void
    fetchEvents: (itemsPerPage: number, page: number) => void
}

class DetailedPage extends React.Component<DetailedPageProps> {
    componentDidMount() {
        this.props.refresh();
    }

    componentDidUpdate(prevProps: DetailedPageProps) {
        if (this.props.match !== prevProps.match) {
            this.props.refresh();
        }
    }

    componentWillUnmount = () => this.props.clearRefresh();

    render() {
        return <LoadableMainContainer
            loadable={this.props.chart}
            main={this.renderMain()}
            header={this.renderHeader()}
        />;
    }

    renderHeader(): React.ReactNode {
        const chartContent = this.props.chart.content;
        if (!chartContent) return null;
        return <Heading.h2>{chartContent.chart.dataTitle || this.props.match.params.subResource}</Heading.h2>;
    }

    renderMain(): React.ReactNode {
        return <ContainerForText>
            {this.renderChart()}
            {this.renderEvents()}
        </ContainerForText>;
    }

    renderChart(): React.ReactNode {
        const chart = this.props.chart.content;
        if (chart === undefined) return null;

        return <Box m={16}><Chart chart={chart.chart} /></Box>;
    }

    renderEvents(): React.ReactNode {
        const events = this.props.events;

        return <Pagination.List
            pageRenderer={p => <Breakdown events={p.items} />}
            page={events.content || emptyPage}
            loading={events.loading}
            onPageChanged={(newPage, page) => this.props.fetchEvents(page.itemsPerPage, newPage)}
        />
    }
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | SetRefreshFunction>, ownProps: OwnProps): Operations => ({
    refresh: () => {
        const { resource, subResource } = ownProps.match.params;
        const fetch = async () => {
            dispatch(Actions.clearResource(resource, subResource));
            dispatch(await Actions.fetchEvents(resource, subResource));
            dispatch(await Actions.fetchChart(resource, subResource));
        };
        fetch();
        dispatch(setRefreshFunction(fetch));
    },

    fetchEvents: async (itemsPerPage, page) => {
        const { resource, subResource } = ownProps.match.params;
        dispatch(await Actions.fetchEvents(resource, subResource, itemsPerPage, page));
    },

    clearRefresh: () => dispatch(setRefreshFunction())
});

const mapStateToProps = (state: ReduxObject, ownProps: OwnProps): StateProps => {
    const { resource, subResource } = ownProps.match.params;
    const name = resourceName(resource, subResource);
    const resourceState = state.accounting.resources[name] || emptyResourceState();
    return {
        events: resourceState.events,
        chart: resourceState.chart
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(DetailedPage);