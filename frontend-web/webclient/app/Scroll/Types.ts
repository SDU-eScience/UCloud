export interface ScrollResult<Item, OffsetType> {
    items: Item[]
    endOfScroll: boolean
    nextOffset: OffsetType | null
}

export function concatScrolls<I, O> (oldScroll: ScrollResult<I, O>, newScroll: ScrollResult<I, O>) {
    return {
        endOfScroll: newScroll.endOfScroll,
        nextOffset: newScroll.nextOffset,
        items: oldScroll.items.concat(newScroll.items)
    };
}

export type ScrollSize = 10 | 25 | 50 | 100 | 250;

export interface ScrollRequest<OffsetType> {
    offset: OffsetType | null
    scrollSize: ScrollSize
}