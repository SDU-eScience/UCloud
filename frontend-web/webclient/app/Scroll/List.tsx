import * as React from "react";
import { ScrollResult, ScrollRequest, ScrollSize } from "./Types";
import { Error, LoadingButton, Flex } from "ui-components";
import Spinner from "LoadingIcon/LoadingIcon";
import * as Heading from "ui-components/Heading";

interface ListProps<Item, OffsetType> {
    scroll?: ScrollResult<Item, OffsetType>
    scrollSize?: ScrollSize

    renderer: (scroll: ScrollResult<Item, OffsetType>) => React.ReactNode
    onNextScrollRequested: (request: ScrollRequest<OffsetType>) => void

    // Loading
    loading: boolean

    // Error handling
    errorMessage?: string | (() => React.ReactNode)
    customEmptyPage?: React.ReactNode
    onErrorDismiss?: () => void
}

export class List<Item, OffsetType> extends React.PureComponent<ListProps<Item, OffsetType>> {
    private get scrollOrDefault(): ScrollResult<Item, OffsetType> {
        return this.props.scroll || { endOfScroll: false, nextOffset: null, items: [] };
    }

    private get scrollSizeOrDefault(): ScrollSize {
        return this.props.scrollSize || 50;
    }

    render() {
        return <>
            {this.renderError()}
            {this.renderBody()}
            {this.renderLoadingButton()}
        </>;
    }

    private renderError(): React.ReactNode {
        const { props } = this;
        if (typeof props.errorMessage == "string") {
            return <Error clearError={props.onErrorDismiss} error={props.errorMessage} />;
        } else if (typeof props.errorMessage == "function") {
            return props.errorMessage();
        } else {
            return null;
        }
    }

    private renderBody(): React.ReactNode {
        const { props } = this;
        if (props.loading && (props.scroll === undefined || props.scroll.items.length === 0)) {
            return (<Spinner size={24} />)
        } else {
            if (props.scroll === undefined || props.scroll.items.length === 0) {
                if (!props.customEmptyPage) {
                    return <div>
                        <Heading.h2>No results.</Heading.h2>
                    </div>;
                } else {
                    return props.customEmptyPage
                }
            } else {
                return props.renderer(props.scroll)
            }
        }
    }

    private renderLoadingButton(): React.ReactNode {
        const { loading, onNextScrollRequested } = this.props;
        const scroll = this.scrollOrDefault;
        const size = this.scrollSizeOrDefault;

        return <Flex justifyContent={"center"}>
            <LoadingButton
                onClick={() => {
                    onNextScrollRequested({
                        offset: scroll.nextOffset,
                        scrollSize: size
                    })
                }}
                loading={loading}
                content={"Load more"}
            />
        </Flex>;
    }
}