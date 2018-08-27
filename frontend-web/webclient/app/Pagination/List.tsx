import * as React from "react";
import { Grid, Message, Header } from "semantic-ui-react";
import { Page } from "Types";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import * as Self from ".";
import "./pagination.scss";
import { ifPresent } from "UtilityFunctions";
import { RefreshButton } from "UtilityComponents";

interface ListProps {
    pageRenderer: (page: Page<any>) => React.ReactNode

    // List state
    loading?: boolean

    // Page results
    page: Page<any>

    // Error properties  
    errorMessage?: string | (() => React.ReactNode | null)
    customEmptyPage?: React.ReactNode

    // Callbacks
    onItemsPerPageChanged: (itemsPerPage: number) => void
    onPageChanged: (newPage: number) => void
    onRefresh?: () => void
    onErrorDismiss?: () => void
}

export class List extends React.PureComponent<ListProps> {
    constructor(props: ListProps) {
        super(props);
    }

    render() {
        const props = this.props;
        const body = this.renderBody();

        let errorComponent: React.ReactNode = null;
        if (typeof props.errorMessage == "string") {
            errorComponent = <Message color="red" onDismiss={props.onErrorDismiss}>{props.errorMessage}</Message>;
        } else if (typeof props.errorMessage == "function") {
            errorComponent = props.errorMessage();
        }

        const refreshButton = !!this.props.onRefresh ? (
            <RefreshButton
                className="pagination-float-right"
                loading={this.props.loading}
                onClick={this.props.onRefresh} 
            />
        ) : null;

        return (
            <React.Fragment>
                {errorComponent}
                {refreshButton}
                <Self.EntriesPerPageSelector
                    content="Items per page"
                    className="items-per-page-padding pagination-float-right"
                    entriesPerPage={props.page.itemsPerPage}
                    onChange={(perPage) => ifPresent(props.onItemsPerPageChanged, (c) => c(perPage))}
                />
                {body}
                <div>
                    <Self.Buttons
                        as="span"
                        currentPage={props.page.pageNumber}
                        toPage={(page) => ifPresent(props.onPageChanged, (c) => c(page))}
                        totalPages={Math.ceil(props.page.itemsInTotal / props.page.itemsPerPage)}
                    />
                </div>
            </React.Fragment>
        );
    }


    private renderBody(): React.ReactNode {
        const props = this.props;
        if (props.loading) {
            return <Grid centered verticalAlign="middle" columns={1}>
                <div className="pagination-loader">
                    <DefaultLoading loading size={undefined} className="pagination-list-loading" />
                </div>
            </Grid>
        } else {
            if (props.page == null || props.page.items.length == 0) {
                if (!props.customEmptyPage) {
                    return <div>
                        <Header as="h2">
                            No results.
                            <a
                                href="#"
                                onClick={(e) => { e.preventDefault(); ifPresent(props.onRefresh, (c) => c()) }}
                            >
                                {" Try again?"}
                            </a>
                        </Header>
                    </div>;
                } else {
                    return props.customEmptyPage
                }
            } else {
                return props.pageRenderer(props.page);
            }
        }
    }
}
