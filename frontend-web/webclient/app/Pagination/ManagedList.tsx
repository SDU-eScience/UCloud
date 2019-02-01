import * as React from "react";
import { emptyPage } from "DefaultObjects";
import * as Self from ".";

export class ManagedList extends React.Component<Self.ManagedListProps, Self.ManagedListState> {
    constructor(props: Self.ManagedListProps) {
        super(props);

        this.state = {
            loading: false,
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
        this.retrieveData(this.state.results.pageNumber, this.state.results.itemsPerPage);
    }

    private retrieveData(page: number, itemsPerPage: number) {
        this.setState(() => ({
            loading: true,
        }));

        this.props.dataProvider(page, itemsPerPage)
            .then(results => {
                this.setState(() => ({ results, loading: false }));
            })
            .catch(e => {
                // TODO Use error message from request
                if (!e.isCanceled)
                    this.setState({ errorMessage: "An error has occured", loading: false });
            });
            /* .finally(() => this.setState({ loading: false })); */
    }

    render() {
        const { loading, results, errorMessage } = this.state;
        const props = this.props;
        return <Self.List
            loading={loading}
            page={results}
            errorMessage={errorMessage}
            pageRenderer={props.pageRenderer}
            onPageChanged={page => this.retrieveData(page, results.itemsPerPage)}
            onErrorDismiss={() => this.setState({ errorMessage: undefined })}
        />
    }
}
