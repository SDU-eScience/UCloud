import * as React from "react";
import { ScrollResult, ScrollRequest, ScrollSize } from "./Types";
import { Error, LoadingButton, Flex } from "ui-components";
import * as Heading from "ui-components/Heading";

interface ListProps<Item, OffsetType> {
    scroll?: ScrollResult<Item, OffsetType>
    scrollSize?: ScrollSize

    frame?: (containerRef: React.RefObject<any>, children) => React.ReactNode
    renderer: (item: Item) => React.ReactNode
    onNextScrollRequested: (request: ScrollRequest<OffsetType>) => void

    // Loading
    loading: boolean

    // Error handling
    errorMessage?: string | (() => React.ReactNode)
    customEmptyPage?: React.ReactNode
    onErrorDismiss?: () => void
}

export class List<Item, OffsetType> extends React.PureComponent<ListProps<Item, OffsetType>> {
    private eventListener: (e: UIEvent) => void
    private container = React.createRef<HTMLElement>();

    private get scrollOrDefault(): ScrollResult<Item, OffsetType> {
        return this.props.scroll || { endOfScroll: false, nextOffset: null, items: [] };
    }

    private get scrollSizeOrDefault(): ScrollSize {
        return this.props.scrollSize || 50;
    }

    componentWillMount() {
        this.eventListener = e => {
            if ((window.innerHeight + window.pageYOffset) >= document.body.offsetHeight - 200) {
                this.requestMore(false);
            }
        };

        window.addEventListener('scroll', this.eventListener);
    }

    componentWillUnmount() {
        window.removeEventListener('scroll', this.eventListener);
    }

    componentDidUpdate() {
        const container = this.container.current;
        console.log(container);
        if(container !== null) {
            let totalHeight = 0;
            for (let i = 0; i < container.children.length; i++) {
                const child = container.children[i];
                console.log(child.clientHeight);
                totalHeight += child.clientHeight;
            }
            console.log("Total height:", totalHeight);
        }
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
            return null;
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
                const children = <ListBody
                    scroll={props.scroll}
                    renderer={props.renderer}
                    containerRef={this.container}
                />;
                const frame = props.frame;
                if (frame !== undefined) {
                    return frame(this.container, children);
                } else {
                    return <>{children}</>;
                }
            }
        }
    }

    private renderLoadingButton(): React.ReactNode {
        const { loading } = this.props;

        return <Flex justifyContent={"center"}>
            <LoadingButton
                onClick={() => this.requestMore(true)}
                loading={loading}
                content={"Load more"}
            />
        </Flex>;
    }

    private requestMore(alwaysLoadMore: boolean) {
        const { loading, onNextScrollRequested } = this.props;
        const scroll = this.scrollOrDefault;
        const size = this.scrollSizeOrDefault;

        if (!loading && (!scroll.endOfScroll || alwaysLoadMore)) {
            onNextScrollRequested({
                offset: scroll.nextOffset,
                scrollSize: size
            })
        }
    }
}

interface ListBodyProps {
    scroll: ScrollResult<any, any>
    renderer: (item: any) => React.ReactNode
    containerRef?: React.RefObject<any>
}

class ListBody extends React.PureComponent<ListBodyProps> {
    shouldComponentUpdate(nextProps: ListBodyProps) {
        const nextLength = nextProps.scroll !== undefined ? nextProps.scroll.items.length : 0;
        const currentLength = this.props.scroll !== undefined ? this.props.scroll.items.length : 0;
        return nextLength !== currentLength;
    }

    render() {
        const { scroll, renderer, containerRef } = this.props;
        if (scroll !== undefined) {
            const items = scroll.items.map(i => renderer(i));
            if (containerRef !== undefined) {
                return <>{items}</>;
            } else {
                return <div ref={containerRef}>{items}</div>;
            }
        } else {
            return null;
        }
    }
}