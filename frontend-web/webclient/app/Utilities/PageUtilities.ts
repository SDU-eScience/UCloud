import { PageV2 } from "@/UCloud";

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
