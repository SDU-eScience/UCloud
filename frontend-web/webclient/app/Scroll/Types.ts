export interface ScrollResult<Item, OffsetType> {
    items: Item[];
    endOfScroll: boolean;
    nextOffset: OffsetType | null;
}

interface ConcatScrolls<T1, T2> {
    endOfScroll: boolean;
    nextOffset: T2 | null;
    items: T1[];
}

export function concatScrolls<I, O>(newScroll: ScrollResult<I, O>, oldScroll?: ScrollResult<I, O>): ConcatScrolls<I, O> {
    const oldItems = oldScroll !== undefined ? oldScroll.items : [];

    return {
        endOfScroll: newScroll.endOfScroll,
        nextOffset: newScroll.nextOffset,
        items: oldItems.concat(newScroll.items)
    };
}

export type ScrollSize = 10 | 25 | 50 | 100 | 250;

export interface ScrollRequest<OffsetType> {
    offset?: OffsetType | null;
    scrollSize: ScrollSize;
}
