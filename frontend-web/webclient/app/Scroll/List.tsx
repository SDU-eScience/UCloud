import * as React from "react";
import { ScrollResult, ScrollRequest, ScrollSize } from "./Types";
import { Error, Flex, Button } from "ui-components";
import * as Heading from "ui-components/Heading";

interface ListProps<Item, OffsetType> {
    scroll?: ScrollResult<Item, OffsetType>
    scrollSize?: ScrollSize

    frame?: (containerRef: React.RefObject<any>, children) => React.ReactNode
    renderer: (props: { item: Item }) => JSX.Element | null
    onNextScrollRequested: (request: ScrollRequest<OffsetType>) => void
    spacer?: (height: number) => React.ReactNode

    // Loading
    loading: boolean

    // Error handling
    errorMessage?: string | (() => React.ReactNode)
    customEmptyPage?: React.ReactNode
    onErrorDismiss?: () => void
}

interface ListState {
    firstVisibleElement?: number
    lastVisibleElement?: number
    spacingRequired?: number
    postSpacingRequired?: number
}

export class List<Item, OffsetType> extends React.Component<ListProps<Item, OffsetType>, ListState> {
    private eventListener: (e: UIEvent) => void
    private container = React.createRef<HTMLElement>();
    private recordedHeights: number[] = [];
    private topOffset: number = -1;
    private averageComponentSize: number = -1;

    private get scrollOrDefault(): ScrollResult<Item, OffsetType> {
        return this.props.scroll || { endOfScroll: false, nextOffset: null, items: [] };
    }

    private get scrollSizeOrDefault(): ScrollSize {
        return this.props.scrollSize || 50;
    }

    constructor(props) {
        super(props);
        this.state = {};
    }

    shouldComponentUpdate(nextProps, nextState) {
        const result = this.state !== nextState || this.props !== nextProps;
        return result;
    }

    componentWillReceiveProps(nextProps: ListProps<Item, OffsetType>) {
        const scroll = this.scrollOrDefault;
        const nextScrollOrDefault = nextProps.scroll || { endOfScroll: false, nextOffset: null, items: [] };

        this.topOffset = -1;

        if (nextScrollOrDefault.items.length < scroll.items.length) {
            this.recordedHeights = Array.from({ length: scroll.items.length }, () => 0);

            this.state = {};
            this.setState({});
        } else {
            // TODO This is not always correct (but often is)
            const missingEntries = nextScrollOrDefault.items.length - scroll.items.length;
            this.recordedHeights = this.recordedHeights.concat(Array.from({ length: missingEntries }, () => 0));

            const firstVisibleElement = this.state.firstVisibleElement;
            if (firstVisibleElement !== undefined && this.averageComponentSize > 0) {
                // TODO We could calculate this in a better way
                const lastVisibleElement = this.state.lastVisibleElement || scroll.items.length;
                this.setState({ lastVisibleElement: lastVisibleElement + 5, postSpacingRequired: this.averageComponentSize * 5 });
            }
        }
    }

    private updateTopOffset() {
        if (this.topOffset === -1) {
            const container = this.container.current;
            if (container !== null) {
                const bodyRect = document.body.getBoundingClientRect();
                const containerRect = container.getBoundingClientRect()
                this.topOffset = containerRect.top - bodyRect.top;
            } else {
                this.topOffset = 0;
            }
        }
    }

    componentWillMount() {
        this.eventListener = e => {
            if ((window.innerHeight + window.pageYOffset) >= document.body.offsetHeight - 200) {
                this.requestMore(false);
            }

            this.updateTopOffset();
            const currentTop = window.scrollY;
            const currentBottom = currentTop + window.innerHeight - this.topOffset;

            const heights = this.recordedHeights;
            let sum = 0;
            let firstVisibleElement: number | undefined;
            let lastVisibleElement: number | undefined;
            let spacingRequired: number | undefined;
            let postSpacingRequired: number | undefined;

            for (let i = 0; i < heights.length; i++) {
                sum += heights[i];
                if (sum >= currentTop && firstVisibleElement === undefined) {
                    firstVisibleElement = i;
                    if (i > 0) {
                        spacingRequired = sum - heights[i];
                    }
                }

                if (sum >= currentBottom && lastVisibleElement === undefined) {
                    lastVisibleElement = i + 1;
                    sum = 0; // Start counting for postSpacingRequired
                }
            }

            if (lastVisibleElement !== undefined && sum > 0) {
                postSpacingRequired = sum;
            }

            const state = this.state;
            if (firstVisibleElement !== state.firstVisibleElement || lastVisibleElement !== state.lastVisibleElement) {
                this.setState(() => ({
                    firstVisibleElement,
                    lastVisibleElement,
                    spacingRequired,
                    postSpacingRequired
                }));
            }
        };

        window.addEventListener('scroll', this.eventListener);
    }

    componentWillUnmount() {
        window.removeEventListener('scroll', this.eventListener);
    }

    componentDidUpdate() {
        const container = this.container.current;
        if (container !== null) {
            const offset = this.state.firstVisibleElement || 0;
            // Skip the spacer
            const initialElement = this.state.spacingRequired !== undefined ? 1 : 0;
            const elementsToSkip = this.state.postSpacingRequired !== undefined ? 1 : 0;

            for (let i = initialElement; i < container.children.length - elementsToSkip; i++) {
                const child = container.children[i];
                const height = child.getBoundingClientRect().height;
                this.recordedHeights[offset + i - initialElement] = height;
            }

            if (this.averageComponentSize === -1) {
                let sum = 0;
                let elementsToCount = 0;
                const recorded = this.recordedHeights;
                for (let i = 0; i < recorded.length; i++) {
                    if (recorded[i] > 0) {
                        sum += recorded[i];
                        elementsToCount++;
                    }
                }

                if (elementsToCount > 0) {
                    this.averageComponentSize = sum / elementsToCount;
                }
            }
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
                const children = this.renderEntries();
                const frame = props.frame;
                if (frame !== undefined) {
                    return frame(this.container, children);
                } else {
                    return <>{children}</>;
                }
            }
        }
    }

    private renderEntries(): React.ReactNode {
        const items = this.scrollOrDefault.items;
        const containerRef = this.container;
        const { renderer, spacer } = this.props;
        const { postSpacingRequired, spacingRequired, firstVisibleElement, lastVisibleElement } = this.state;

        return <ListBody
            containerRef={containerRef}
            items={items}
            firstVisibleElement={firstVisibleElement}
            lastVisibleElement={lastVisibleElement}
            spacingRequired={spacingRequired}
            postSpacingRequired={postSpacingRequired}
            renderer={renderer}
            spacer={spacer}
        />;
    }

    private renderLoadingButton(): React.ReactNode {
        const { loading } = this.props;

        return <Flex justifyContent={"center"}>
            <Button
                onClick={() => this.requestMore(true)}
                disabled={loading}
            >Load more</Button>
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
    spacingRequired?: number
    postSpacingRequired?: number
    firstVisibleElement?: number
    lastVisibleElement?: number
    containerRef?: React.RefObject<any>
    items: any[]
    renderer: (props: { item: any }) => JSX.Element | null;
    spacer?: (height: number) => React.ReactNode
}

class ListBody extends React.Component<ListBodyProps> {
    shouldComponentUpdate(nextProps: ListBodyProps) {
        const { props } = this;
        return props.spacingRequired !== nextProps.spacingRequired ||
            props.postSpacingRequired !== nextProps.postSpacingRequired ||
            props.firstVisibleElement !== nextProps.firstVisibleElement ||
            props.lastVisibleElement !== nextProps.lastVisibleElement ||
            props.items.length !== nextProps.items.length;
    }

    render() {
        const { spacingRequired, postSpacingRequired, firstVisibleElement, lastVisibleElement,
            containerRef, renderer, spacer, items } = this.props;

        const first = Math.max(0, firstVisibleElement || 0);
        const last = Math.min(items.length, lastVisibleElement !== undefined ? lastVisibleElement : items.length);

        const children: React.ReactNode[] = [];

        const spacerOrDefault = spacer || ((height: number) => <div style={{ height: `${height}px` }} />);
        if (spacingRequired !== undefined) {
            children.push(spacerOrDefault(spacingRequired));
        }

        for (let i = first; i < last; i++) {
            const Renderer = renderer;
            children.push(<Renderer key={i} item={items[i]} />);
        }

        if (postSpacingRequired !== undefined) {
            children.push(spacerOrDefault(postSpacingRequired));
        }

        if (containerRef !== undefined) {
            return <>{children}</>;
        } else {
            return <div ref={containerRef}>{children}</div>;
        }
    }
}