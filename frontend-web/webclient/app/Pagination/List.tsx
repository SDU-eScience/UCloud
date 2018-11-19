import * as React from "react";
import { Page } from "Types";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import * as Self from ".";
import { ifPresent } from "UtilityFunctions";
import { RefreshButton } from "UtilityComponents";
import * as Heading from "ui-components/Heading";
import { Box, Flex, Relative, Error } from "ui-components";

interface ListProps {
    pageRenderer: (page: Page<any>) => React.ReactNode

    // List state
    loading?: boolean

    // Page results
    page: Page<any>
    customEntriesPerPage?: boolean

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
                onClick={this.props.onRefresh}
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
                            onChange={perPage => ifPresent(props.onItemsPerPageChanged, c => c(perPage))}
                        /> : null}
                        {refreshButton}
                    </Relative>
                </Flex>
                {body}
                <div>
                    <Self.Buttons
                        as="span"
                        currentPage={props.page.pageNumber}
                        toPage={(page) => ifPresent(props.onPageChanged, c => c(page))}
                        totalPages={props.page.pagesInTotal}
                    />
                </div>
            </>
        );
    }


    private renderBody(): React.ReactNode {
        const { props } = this;
        if (props.loading) {
            return (<Flex><Box width="50%" /><DefaultLoading loading/></Flex>)
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
                return props.pageRenderer(props.page);
            }
        }
    }
}
