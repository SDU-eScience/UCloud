import * as React from "react";
import { emptyPage } from "DefaultObjects";
import * as Self from ".";

export class ManagedList extends React.Component<Self.ManagedListProps, Self.ManagedListState> {
    constructor(props: Self.ManagedListProps) {
        super(props);

        this.state = {
            loading: false,
            currentPage: 0,
            itemsPerPage: 10,
            results: emptyPage,
            dataProvider: (page: number, itemsPerPage: number) => emptyPage
        };
    }

    componentDidMount() {
        this.refresh();
    }

    componentDidUpdate() {
        if (this.state.dataProvider !== this.props.dataProvider) {
            this.setState(() => ({ dataProvider: this.props.dataProvider }));
            this.refresh();
        }
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
            page={state.results}
            errorMessage={state.errorMessage}
            pageRenderer={props.pageRenderer}
            onItemsPerPageChanged={itemsPerPage => this.retrieveData(0, itemsPerPage)}
            onPageChanged={page => this.retrieveData(page, state.itemsPerPage)}
            onRefresh={() => this.refresh()}
            onErrorDismiss={() => this.setState({ errorMessage: null })}
        />
    }
}
