import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import * as Heading from "@/ui-components/Heading";
import {ifPresent} from "@/UtilityFunctions";
import * as Self from ".";

interface ListProps<T> {
    pageRenderer: (page: Page<T>) => React.ReactNode;

    // List state
    loading: boolean;

    // Page results
    page: Page<T>;

    customEmptyPage?: React.ReactNode;

    // Callbacks
    onPageChanged: (newPage: number, page: Page<T>) => void;
}

export class List<T> extends React.PureComponent<ListProps<T>> {
    public render(): React.ReactNode {
        const {props} = this;
        const body = this.renderBody();

        return (
            <>
                {body}
                <Self.PaginationButtons
                    currentPage={props.page.pageNumber}
                    toPage={page => ifPresent(props.onPageChanged, c => c(page, props.page))}
                    totalPages={Math.ceil(props.page.itemsInTotal / props.page.itemsPerPage)}
                />
            </>
        );
    }


    private renderBody(): React.ReactNode {
        const {props} = this;
        if (props.loading && props.page.items.length === 0) {
            return (<Spinner />);
        } else {
            if (props.page == null || props.page.items.length === 0) {
                if (!props.customEmptyPage) {
                    return <div><Heading.h2>No results.</Heading.h2></div>;
                } else {
                    return props.customEmptyPage;
                }
            } else {
                return props.pageRenderer(props.page);
            }
        }
    }
}
