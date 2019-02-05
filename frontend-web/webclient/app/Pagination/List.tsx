import * as React from "react";
import { Page } from "Types";
import * as Self from ".";
import { ifPresent } from "UtilityFunctions";
import * as Heading from "ui-components/Heading";
import { Error } from "ui-components";
import Spinner from "LoadingIcon/LoadingIcon";
import { emptyPage } from "DefaultObjects";

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
    onPageChanged: (newPage: number, page: Page<T>) => void
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
        
        return (
            <>
                {errorComponent}
                {body}
                <Self.PaginationButtons
                    currentPage={props.page.pageNumber}
                    toPage={page => ifPresent(props.onPageChanged, c => c(page, props.page))}
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
                        </Heading.h2>
                    </div>;
                } else {
                    return props.customEmptyPage
                }
            } else {
                return props.pageRenderer(props.page)
            }
        }
    }
}
