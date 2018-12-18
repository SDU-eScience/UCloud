import * as React from "react";
import * as Actions from "./Redux/AccountingActions";
import { connect } from "react-redux";
import { ReduxObject, emptyPage } from "DefaultObjects";
import { Dispatch } from "redux";
import { match } from "react-router-dom";
import { LoadableContent } from "LoadableContent";
import * as API from "./api";
import { Page } from "Types";
import { resourceName, emptyResourceState } from "./Redux/AccountingObject";
import { LoadingMainContainer } from "MainContainer/MainContainer";
import { Chart, Breakdown } from "Accounting";
import * as Heading from "ui-components/Heading";
import { ContainerForText, Box } from "ui-components";
import * as Pagination from "Pagination";

type DetailedPageProps = OwnProps & StateProps & Operations;

interface OwnProps {
    match: match<{ resource: string, subResource: string }>
}

interface StateProps {
    events: LoadableContent<Page<API.AccountingEvent>>
    chart: LoadableContent<API.ChartResponse>
}

interface Operations {
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

    render() {
        return <LoadingMainContainer
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
            errorMessage={events.error ? events.error.errorMessage : undefined}
            onRefresh={page => this.props.fetchEvents(page.itemsPerPage, page.pageNumber)}
            onItemsPerPageChanged={(itemsPerPage) => this.props.fetchEvents(itemsPerPage, 0)}
            onPageChanged={(newPage, page) => this.props.fetchEvents(page.itemsPerPage, newPage)}
        />
    }
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type>, ownProps: OwnProps): Operations => {
    return {
        refresh: async () => {
            const { resource, subResource } = ownProps.match.params;
            dispatch(await Actions.fetchEvents(resource, subResource));
            dispatch(await Actions.fetchChart(resource, subResource));
        },

        fetchEvents: async (itemsPerPage, page) => {
            const { resource, subResource } = ownProps.match.params;
            dispatch(await Actions.fetchEvents(resource, subResource, itemsPerPage, page));
        }
    };
};

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