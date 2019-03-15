import * as React from "react";
import { ScrollResult, ScrollRequest, ScrollSize } from "./Types";
import { Error, LoadingButton, Flex, Box } from "ui-components";
import * as Heading from "ui-components/Heading";
import { Dictionary } from "Types";
import { timestampUnixMs } from "UtilityFunctions";

interface ListProps<Item, OffsetType> {
    scroll?: ScrollResult<Item, OffsetType>
    scrollSize?: ScrollSize

    frame?: (containerRef: React.RefObject<any>, children) => React.ReactNode
    renderer: (item: Item, inlineStyle: React.CSSProperties) => React.ReactNode
    onNextScrollRequested: (request: ScrollRequest<OffsetType>) => void

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

export class List<Item, OffsetType> extends React.PureComponent<ListProps<Item, OffsetType>, ListState> {
    private eventListener: (e: UIEvent) => void
    private container = React.createRef<HTMLElement>();
    private recordedHeights: number[] = [];
    private currentTick: number = -1;

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

    componentWillReceiveProps(nextProps: ListProps<Item, OffsetType>) {
        const scroll = this.scrollOrDefault;
        const nextScrollOrDefault = nextProps.scroll || { endOfScroll: false, nextOffset: null, items: [] };

        if (nextScrollOrDefault.items.length < scroll.items.length) {
            this.recordedHeights = Array.from({ length: scroll.items.length }, () => 0);
        } else {
            // TODO This is an incorrect assumption. It will only work for prototyping.
            const missingEntries = nextScrollOrDefault.items.length - scroll.items.length;
            this.recordedHeights.concat(Array.from({ length: missingEntries }, () => 0));
        }
    }

    componentWillMount() {
        this.eventListener = e => {
            if ((window.innerHeight + window.pageYOffset) >= document.body.offsetHeight - 10) {
                this.requestMore(false);
            }
            const topOffset = 144;
            const currentTop = window.scrollY;
            const currentBottom = currentTop + window.innerHeight - topOffset;

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
                this.recordedHeights[offset + i - initialElement] = child.getBoundingClientRect().height;
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
        if (items.length === 0) return null;

        const containerRef = this.container;
        const { renderer } = this.props;
        const { postSpacingRequired, spacingRequired, firstVisibleElement, lastVisibleElement } = this.state;
        const first = firstVisibleElement || 0;
        const last = lastVisibleElement !== undefined ? lastVisibleElement : items.length;

        const children: React.ReactNode[] = [];

        if (spacingRequired !== undefined) {
            children.push(<Box height={spacingRequired} />);
        }

        for (let i = first; i < last; i++) {
            children.push(renderer(items[i], {}));
        }

        if (postSpacingRequired !== undefined) {
            children.push(<Box height={postSpacingRequired} />);
        }

        if (containerRef !== undefined) {
            return <>{children}</>;
        } else {
            return <div ref={containerRef}>{children}</div>;
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
    renderer: (item: any, style: React.CSSProperties) => React.ReactNode
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
            const items = scroll.items.map(i => renderer(i, {}));
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