import {Breakdown, Chart} from "Accounting";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {LoadableContent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction, SetRefreshFunction} from "Navigation/Redux/HeaderActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {match} from "react-router-dom";
import {Dispatch} from "redux";
import {ClearRefresh, Page} from "Types";
import {Box, ContainerForText} from "ui-components";
import * as Heading from "ui-components/Heading";
import * as API from "./api";
import * as Actions from "./Redux/AccountingActions";
import {emptyResourceState, resourceName} from "./Redux/AccountingObject";

type DetailedPageProps = OwnProps & StateProps & Operations;

interface OwnProps {
    match: match<{resource: string, subResource: string}>;
}

interface StateProps {
    events: LoadableContent<Page<API.AccountingEvent>>;
    chart: LoadableContent<API.ChartResponse>;
}

interface Operations extends ClearRefresh {
    refresh: () => void;
    fetchEvents: (itemsPerPage: number, page: number) => void;
}

class DetailedPage extends React.Component<DetailedPageProps> {
    public componentDidMount() {
        this.props.refresh();
    }

    public componentDidUpdate(prevProps: DetailedPageProps) {
        if (this.props.match !== prevProps.match) {
            this.props.refresh();
        }
    }

    public componentWillUnmount() {
        this.props.clearRefresh();
    }

    public render() {
        return <LoadableMainContainer
            loadable={this.props.chart}
            main={this.renderMain()}
            header={this.renderHeader()}
        />;
    }

    public renderHeader(): React.ReactNode {
        const chartContent = this.props.chart.content;
        if (!chartContent) return null;
        return <Heading.h2>{chartContent.chart.dataTitle || this.props.match.params.subResource}</Heading.h2>;
    }

    public renderMain(): React.ReactNode {
        return <ContainerForText>
            {this.renderChart()}
            {this.renderEvents()}
        </ContainerForText>;
    }

    public renderChart(): React.ReactNode {
        const chart = this.props.chart.content;
        if (chart === undefined) return null;

        return <Box m={16}><Chart chart={chart.chart} /></Box>;
    }

    public renderEvents(): React.ReactNode {
        const events = this.props.events;

        return <Pagination.List
            pageRenderer={p => <Breakdown events={p.items} />}
            page={events.content || emptyPage}
            loading={events.loading}
            onPageChanged={(newPage, page) => this.props.fetchEvents(page.itemsPerPage, newPage)}
        />;
    }
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | SetRefreshFunction>, ownProps: OwnProps): Operations => ({
    refresh: () => {
        const {resource, subResource} = ownProps.match.params;
        const fetch = async () => {
            dispatch(Actions.clearResource(resource, subResource));
            dispatch(await Actions.fetchEvents(resource, subResource));
            dispatch(await Actions.fetchChart(resource, subResource));
        };
        fetch();
        dispatch(setRefreshFunction(fetch));
    },

    fetchEvents: async (itemsPerPage, page) => {
        const {resource, subResource} = ownProps.match.params;
        dispatch(await Actions.fetchEvents(resource, subResource, itemsPerPage, page));
    },

    clearRefresh: () => dispatch(setRefreshFunction())
});

const mapStateToProps = (state: ReduxObject, ownProps: OwnProps): StateProps => {
    const {resource, subResource} = ownProps.match.params;
    const name = resourceName(resource, subResource);
    const resourceState = state.accounting.resources[name] || emptyResourceState();
    return {
        events: resourceState.events,
        chart: resourceState.chart
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(DetailedPage);