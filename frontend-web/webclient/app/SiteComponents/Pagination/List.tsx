import * as React from "react";
import { Message, Header, Pagination, Dropdown } from "semantic-ui-react";
import { Page } from "../../types/types";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import * as Self from ".";

interface ListProps {
    pageRenderer: (page: Page<any>) => React.ReactNode

    // List state
    loading?: boolean

    // Page results
    currentPage?: number
    itemsPerPage?: number
    results?: Page<any>

    // Error properties
    errorMessage?: string | (() => React.ReactNode | null)

    // Callbacks
    onItemsPerPageChanged?: (itemsPerPage: number) => void
    onPageChanged?: (newPage: number) => void
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

        let errorComponent = null
        if (typeof props.errorMessage == "string") {
            errorComponent = <Message color='red' onDismiss={props.onErrorDismiss}>{props.errorMessage}</Message>;
        } else if (typeof props.errorMessage == "function") {
            errorComponent = props.errorMessage();
        }

        return (
            <div>
                {errorComponent}

                {body}

                <Self.Buttons
                    currentPage={props.currentPage}
                    toPage={(page) => ifPresent(props.onPageChanged, (c) => c(page))}
                    totalPages={props.results.itemsInTotal / props.itemsPerPage}
                />

                <Self.EntriesPerPageSelector
                    content="Results per page"
                    entriesPerPage={props.itemsPerPage}
                    onChange={(perPage) => ifPresent(props.onItemsPerPageChanged, (c) => c(perPage))}
                />
            </div>
        );
    }


    private renderBody(): React.ReactNode {
        const props = this.props;
        if (props.loading) {
            return <BallPulseLoading loading />
        } else {
            if (props.results == null || props.results.items.length == 0) {
                return <div>
                    <Header as="h2">
                        No results. <a href="#">Try again?</a>
                    </Header>
                </div>;
            } else {
                return props.pageRenderer(props.results);
            }
        }
    }
}

function ifPresent(f, handler: (f: any) => void) {
    if (f) handler(f);
}