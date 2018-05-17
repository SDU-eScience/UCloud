import * as React from "react";
import { Message, Header, Pagination, Dropdown } from "semantic-ui-react";
import { Page } from "../../types/types";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import * as Self from ".";

interface ManagedListProps {
    dataProvider: (page: number, itemsPerPage: number) => Promise<Page<any>>
    pageRenderer: (page: Page<any>) => React.ReactNode
}

interface ManagedListState {
    loading: boolean
    currentPage: number
    itemsPerPage: number
    results: Page<any>
    errorMessage?: string
}

export class ManagedList extends React.Component<ManagedListProps, ManagedListState> {
    constructor(props: ManagedListProps) {
        super(props);

        this.state = {
            loading: false,
            currentPage: 0,
            itemsPerPage: 10,
            results: { items: [], itemsPerPage: 10, itemsInTotal: 0, pageNumber: 0 }
        };
    }

    private refresh() {
        this.retrieveData(this.state.currentPage, this.state.itemsPerPage);
    }

    private retrieveData(page: number, itemsPerPage: number) {
        this.setState(() => ({
            loading: true,
            currentPage: page,
            itemsPerPage,
        }));

        this.props.dataProvider(page, itemsPerPage)
            .then(results => {
                this.setState(() => ({ results }));
            })
            .catch(e => {
                // TODO Use error message from request
                this.setState({ errorMessage: "An error has occured" });
            })
            .then(() => {
                this.setState({ loading: false });
            });
    }

    render() {
        const state = this.state;
        const props = this.props;
        return <Self.List
            loading={state.loading}
            currentPage={state.currentPage}
            itemsPerPage={state.itemsPerPage}
            results={state.results}
            errorMessage={state.errorMessage}
            pageRenderer={props.pageRenderer}
            onItemsPerPageChanged={itemsPerPage => this.retrieveData(0, itemsPerPage)}
            onPageChanged={page => this.retrieveData(page, state.itemsPerPage)}
            onRefresh={() => this.refresh()}
            onErrorDismiss={() => this.setState({ errorMessage: null })}
        />
    }
}
