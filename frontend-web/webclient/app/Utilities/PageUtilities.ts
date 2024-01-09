import {PageV2} from "@/UCloud";

export async function fetchAll<T>(paginator: (next?: string) => Promise<PageV2<T>>, limit: number = 5000): Promise<T[]> {
    const result: T[] = [];
    let next: string | undefined = undefined;

    while (true) {
        const page = await paginator(next);
        result.push(...page.items);
        if (page.next == null) break;
        if (result.length >= limit) break;
        next = page.next;
    }

    return result;
}

export const emptyPage: Readonly<Page<any>> =
    {items: [], itemsInTotal: 0, itemsPerPage: 25, pageNumber: 0};

export const emptyPageV2: Readonly<PageV2<any>> =
    {items: [], itemsPerPage: 100};

