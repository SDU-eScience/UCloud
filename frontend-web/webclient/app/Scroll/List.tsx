import * as React from "react";
import {Button, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {ScrollRequest, ScrollResult, ScrollSize} from "./Types";

interface ListProps<Item, OffsetType> {
    scroll?: ScrollResult<Item, OffsetType>;
    scrollSize?: ScrollSize;

    frame?: (containerRef: React.RefObject<any>, children) => React.ReactNode;
    renderer: (props: {item: Item}) => JSX.Element | null;
    onNextScrollRequested: (request: ScrollRequest<OffsetType>) => void;
    spacer?: (height: number) => React.ReactNode;
    loading: boolean;

    customEmptyPage?: React.ReactNode;
}

interface ListState {
    firstVisibleElement?: number
    lastVisibleElement?: number
    spacingRequired?: number
    postSpacingRequired?: number
}

export class List<Item, OffsetType> extends React.Component<ListProps<Item, OffsetType>, ListState> {
    private eventListener: (e: UIEvent) => void;
    private container = React.createRef<HTMLElement>();
    private recordedHeights: number[] = [];
    private topOffset: number = -1;
    private averageComponentSize: number = -1;

    private get scrollOrDefault(): ScrollResult<Item, OffsetType> {
        return this.props.scroll || {endOfScroll: false, nextOffset: null, items: []};
    }

    private get scrollSizeOrDefault(): ScrollSize {
        return this.props.scrollSize || 50;
    }

    constructor(props: ListProps<Item, OffsetType>) {
        super(props);
        this.state = {};
    }

    public shouldComponentUpdate(nextProps: ListProps<Item, OffsetType>, nextState: ListState) {
        return this.state !== nextState || this.props !== nextProps;
    }

    public getSnapshotBeforeUpdate(prevProps: Readonly<ListProps<Item, OffsetType>>, prevState: Readonly<ListState>) {
        const scroll = prevProps.scroll || {endOfScroll: false, nextOffset: null, items: []};
        const nextScrollOrDefault = this.scrollOrDefault;

        this.topOffset = -1;

        if (nextScrollOrDefault.items.length < scroll.items.length) {
            this.recordedHeights = Array.from({length: scroll.items.length}, () => 0);
        } else {
            // TODO This is not always correct (but often is)
            const missingEntries = nextScrollOrDefault.items.length - scroll.items.length;
            this.recordedHeights = this.recordedHeights.concat(Array.from({length: missingEntries}, () => 0));
        }
        return null;
    }

    public componentDidMount() {
        // Attach scroll listener
        this.eventListener = e => {
            // Request more when we reach the end of the page
            if ((window.innerHeight + window.pageYOffset) >= document.body.offsetHeight - 200) {
                this.requestMore(false);
            }

            // Calculate the top and bottom of the scroll component. The topOffset contains how many pixels from the to
            // of the document we are offset by.
            if (this.topOffset === -1) {
                const container = this.container.current;
                if (container !== null) {
                    const bodyRect = document.body.getBoundingClientRect();
                    const containerRect = container.getBoundingClientRect();
                    this.topOffset = containerRect.top - bodyRect.top;
                } else {
                    this.topOffset = 0;
                }
            }

            const currentTop = window.scrollY;
            const currentBottom = currentTop + window.innerHeight - this.topOffset;

            // In this code we determine:
            //  - Which elements should be visible (firstVisibleElement and lastVisibleElement)
            //  - How much spacing to add before and after the visible elements (spacingRequired and
            //    postSpacingRequired)

            // The 'heights' property contains the previously recorded heights of components. After the scroller
            // updates we record the heights of the rendered children.
            const heights = this.recordedHeights;

            // The 'sum' is used for counting amount of spacing required. This is used for pre- and post-spacing.
            // We reset the property after having found the lastVisibleElement.
            //
            // Once the sum gets bigger than the currentTop we know that we have found the firstVisibleElement.
            // Similarly, when we get bigger than currentBottom we have found the lastVisibleElement.
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

            // We only update the state if the visible elements have changed.
            // TODO(Dan): I assume that this is for performance reasons, but I don't actually remember why.
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
                this.recordedHeights[offset + i - initialElement] = child.getBoundingClientRect().height;
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
            {this.renderBody()}
            {this.renderLoadingButton()}
        </>;
    }

    private renderBody(): React.ReactNode {
        const {props} = this;
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
        const {renderer, spacer} = this.props;
        const {postSpacingRequired, spacingRequired, firstVisibleElement, lastVisibleElement} = this.state;

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
        const {loading} = this.props;

        return <Flex justifyContent={"center"}>
            <Button
                onClick={() => this.requestMore(true)}
                disabled={loading}
            >Load more</Button>
        </Flex>;
    }

    private requestMore(alwaysLoadMore: boolean) {
        const {loading, onNextScrollRequested} = this.props;
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
    spacingRequired?: number;
    postSpacingRequired?: number;
    firstVisibleElement?: number;
    lastVisibleElement?: number;
    containerRef?: React.RefObject<any>;
    items: any[];
    renderer: (props: {item: any}) => JSX.Element | null;
    spacer?: (height: number) => React.ReactNode;
}

class ListBody extends React.Component<ListBodyProps> {
    public shouldComponentUpdate(nextProps: ListBodyProps) {
        const {props} = this;
        return props.spacingRequired !== nextProps.spacingRequired ||
            props.postSpacingRequired !== nextProps.postSpacingRequired ||
            props.firstVisibleElement !== nextProps.firstVisibleElement ||
            props.lastVisibleElement !== nextProps.lastVisibleElement ||
            props.items.length !== nextProps.items.length;
    }

    public render() {
        const {
            spacingRequired, postSpacingRequired, firstVisibleElement, lastVisibleElement,
            containerRef, renderer, spacer, items
        } = this.props;

        const first = Math.max(0, firstVisibleElement || 0);
        const last = Math.min(items.length, lastVisibleElement !== undefined ? lastVisibleElement : items.length);

        const children: React.ReactNode[] = [];

        const spacerOrDefault = spacer || ((height: number) => <div key={`spacer${height}`} style={{height: `${height}px`}} />);
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
