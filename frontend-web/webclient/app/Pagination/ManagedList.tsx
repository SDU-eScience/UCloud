import * as React from "react";
import {emptyPage} from "DefaultObjects";
import * as Self from ".";
import {errorMessageOrDefault} from "UtilityFunctions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";

export class ManagedList extends React.Component<Self.ManagedListProps, Self.ManagedListState> {
    constructor(props: Self.ManagedListProps) {
        super(props);

        this.state = {
            loading: false,
            results: emptyPage,
            dataProvider: (page: number, itemsPerPage: number) => emptyPage
        };
    }

    public componentDidMount() {
        this.refresh();
    }
    
    public componentDidUpdate() {
        if (this.state.dataProvider !== this.props.dataProvider) {
            this.setState(() => ({dataProvider: this.props.dataProvider}));
            this.refresh();
        }
    }

    private refresh() {
        this.retrieveData(this.state.results.pageNumber, this.state.results.itemsPerPage);
    }

    private async retrieveData(page: number, itemsPerPage: number) {
        this.setState(() => ({
            loading: true,
        }));
        try {
            const results = await this.props.dataProvider(page, itemsPerPage);
            this.setState(() => ({results}));
        } catch (e) {
            if (!e.isCanceled) snackbarStore.addSnack({
                message: errorMessageOrDefault(e, "An error occurred"),
                type: SnackType.Failure
            });
        } finally {
            this.setState({loading: false});
        }
    }

    render() {
        const {loading, results} = this.state;
        const props = this.props;
        return <Self.List
            loading={loading}
            page={results}
            pageRenderer={props.pageRenderer}
            onPageChanged={page => this.retrieveData(page, results.itemsPerPage)}
        />
    }
}
