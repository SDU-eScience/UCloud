import * as React from "react";
import * as UCloud from "@/UCloud";
import {PropsWithChildren, useState} from "react";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, Error} from "@/ui-components";
import {useEffectSkipMount} from "@/UtilityFunctions";

export type PageRenderer<T> = (page: T[], opts: { hasNext: boolean }) => React.ReactNode;

interface ListV2Props<T> {
    page: UCloud.PageV2<T>;
    pageRenderer: PageRenderer<T>;
    loading: boolean;
    customEmptyPage?: React.ReactNode;
    onLoadMore: () => void;
    error?: string;

    // This can be used to reset infinite scrolling
    infiniteScrollGeneration?: number;
    dataIsStatic?: boolean;
}

export function ListV2<T>(props: PropsWithChildren<ListV2Props<T>>): JSX.Element {
    // eslint-disable-next-line
    const [allItems, setAllItems] = useState<T[]>(props.page.items);

    useEffectSkipMount(() => {
        if (props.dataIsStatic !== true) {
            setAllItems([]);
        }
    }, [props.infiniteScrollGeneration, props.dataIsStatic]);

    useEffectSkipMount(() => {
        if (props.dataIsStatic !== true) {
            setAllItems(oldItems => {
                return Array.prototype.concat(oldItems, props.page.items);
            });
        } else {
            setAllItems(props.page.items);
        }
    }, [props.page, props.dataIsStatic]);

    if (props.loading && props.page.items.length === 0) {
        return <HexSpin />;
    }

    if (props.error) {
        return <Box mt="6px"><Error error={props.error} /></Box>;
    }

    if (props.page.items.length === 0) {
        if (!props.customEmptyPage) {
            return <div><Heading.h4>No results.</Heading.h4></div>;
        } else {
            return <>{props.customEmptyPage}</>;
        }
    }

    return <Box>
        {props.pageRenderer(allItems, { hasNext: props.page.next !== null })}
        {props.page.next || allItems.length > 1 ?
            <Box margin={"0 auto"} maxWidth={"500px"}>
                {!props.page.next ? null :
                    <Button fullWidth type={"button"} onClick={props.onLoadMore}>
                        Load more
                    </Button>
                }
            </Box> : null
        }
    </Box>;
}
