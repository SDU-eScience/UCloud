import * as React from "react";
import { Page } from "Types";
import * as Self from ".";
import { ifPresent } from "UtilityFunctions";
import { RefreshButton } from "UtilityComponents";
import * as Heading from "ui-components/Heading";
import { Box, Flex, Relative, Error } from "ui-components";
import Spinner from "LoadingIcon/LoadingIcon";
import { emptyPage } from "DefaultObjects";
import { LoadingBox } from "ui-components/LoadingBox";

interface ListProps<T> {
    pageRenderer: (page: Page<T>) => React.ReactNode

    // List state
    loading: boolean

    // Page results
    page: Page<T>
    customEntriesPerPage?: boolean

    // Error properties  
    errorMessage?: string | (() => React.ReactNode | null)
    customEmptyPage?: React.ReactNode

    // Callbacks
    onItemsPerPageChanged: (itemsPerPage: number, page: Page<T>) => void
    onPageChanged: (newPage: number, page: Page<T>) => void
    onRefresh?: (page: Page<T>) => void
    onErrorDismiss?: () => void
}

export class List<T> extends React.PureComponent<ListProps<T>> {
    constructor(props: ListProps<T>) {
        super(props);
    }

    render() {
        const { props } = this;
        const body = this.renderBody();

        let errorComponent: React.ReactNode = null;
        if (typeof props.errorMessage == "string") {
            errorComponent = <Error clearError={props.onErrorDismiss} error={props.errorMessage} />;
        } else if (typeof props.errorMessage == "function") {
            errorComponent = props.errorMessage();
        }

        const refreshButton = !!this.props.onRefresh ? (
            <RefreshButton
                loading={this.props.loading}
                onClick={() => this.props.onRefresh ? this.props.onRefresh(this.props.page) : null}
            />
        ) : null;
        return (
            <>
                {errorComponent}
                <Flex alignItems="right">
                    <Box ml="auto" />
                    <Relative>
                        {!props.customEntriesPerPage ? <Self.EntriesPerPageSelector
                            content="Items per page"
                            entriesPerPage={props.page.itemsPerPage}
                            onChange={perPage => ifPresent(props.onItemsPerPageChanged, c => c(perPage, props.page))}
                        /> : null}
                        {refreshButton}
                    </Relative>
                </Flex>
                {body}
                <Self.PaginationButtons
                    currentPage={props.page.pageNumber}
                    toPage={(page) => ifPresent(props.onPageChanged, c => c(page, props.page))}
                    totalPages={props.page.pagesInTotal}
                />
            </>
        );
    }


    private renderBody(): React.ReactNode {
        const { props } = this;
        if (props.loading && props.page === emptyPage) {
            return (<Spinner size={24}/>)
        } else {
            if (props.page == null || props.page.items.length == 0) {
                if (!props.customEmptyPage) {
                    return <div>
                        <Heading.h2>
                            No results.
                            <a
                                href="#"
                                onClick={(e) => { e.preventDefault(); ifPresent(props.onRefresh, c => c()) }}
                            >
                                {" Try again?"}
                            </a>
                        </Heading.h2>
                    </div>;
                } else {
                    return props.customEmptyPage
                }
            } else {
                return <LoadingBox loading={props.loading}>{props.pageRenderer(props.page)}</LoadingBox>;
            }
        }
    }
}
