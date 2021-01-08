import * as React from "react";
import * as UCloud from "UCloud";
import {PropsWithChildren, useEffect, useState} from "react";
import HexSpin from "LoadingIcon/LoadingIcon";
import * as Heading from "ui-components/Heading";
import {Box, Button} from "ui-components";

export type PageRenderer<T> = (page: UCloud.PageV2<T>) => React.ReactNode;

interface ListV2Props<T> {
    page: UCloud.PageV2<T>;
    pageRenderer: PageRenderer<T>;
    loading: boolean;
    customEmptyPage?: React.ReactNode;
    onLoadMore: () => void;

    // This can be used to reset infinite scrolling
    infiniteScrollGeneration?: number;
}

type ListV2Type = <T>(props: PropsWithChildren<ListV2Props<T>>, context?: any) => JSX.Element;
export const ListV2: ListV2Type = props => {
    const [allPages, setAllPages] = useState<React.ReactNode[]>([]);
    useEffect(() => {
        setAllPages([]);
    }, [props.infiniteScrollGeneration, props.pageRenderer]);

    useEffect(() => {
        setAllPages(oldPages => {
            return [...oldPages, props.pageRenderer(props.page)];
        });
    }, [props.page, props.pageRenderer]);

    if (props.loading && props.page.items.length === 0 && allPages.length === 1) {
        return <HexSpin/>;
    }

    if (props.page.items.length === 0 && allPages.length === 1) {
        if (!props.customEmptyPage) {
            return <div><Heading.h4>No results.</Heading.h4></div>
        } else {
            return <>{props.customEmptyPage}</>;
        }
    }

    return <Box>
        {allPages.map((node, i) => <React.Fragment key={i}>{node}</React.Fragment>)}
        {props.page.next || allPages.length > 1 ?
            <Box margin={"0 auto"} maxWidth={"500px"}>
                <Button fullWidth type={"button"} onClick={props.onLoadMore} disabled={!props.page.next}>
                    {!props.page.next ? "No more results returned from UCloud" : "Load more"}
                </Button>
            </Box> : null
        }
    </Box>;
}
