import deepcopy from "deepcopy";
import Fuse from "fuse.js";
import {
    computeStringSimilarity,
    precomputeStringSimilarityProfile,
    StringSimilarityProfile
} from "@/Utilities/StringSimilarity";

export function associateBy<T>(items: T[], keySelector: (t: T) => string): Record<string, T> {
    const result: Record<string, T> = {};
    items.forEach(item => {
        const key = keySelector(item);
        result[key] = item;
    });
    return result;
}

export function groupBy<T>(items: T[], keySelector: (t: T) => string): Record<string, T[]> {
    const result: Record<string, T[]> = {};
    items.forEach(item => {
        const key = keySelector(item);
        const existing = result[key] ?? [];
        existing.push(item);
        result[key] = existing;
    });
    return result;
}

export function takeLast<T>(items: T[], numberOfItems: number): T[] {
    return items.slice(Math.max(0, items.length - numberOfItems));
}

export function deepCopy<T>(item: T): T {
    return deepcopy(item);
}

export function createRecordFromArray<T, V>(array: T[], keyValueMapper: (value: T) => [string, V]): Record<string, V> {
    const result: Record<string, V> = {};
    for (const elem of array) {
        const [k, v] = keyValueMapper(elem);
        result[k] = v;
    }
    return result;
}

export function fuzzySearch<T, K extends keyof T>(array: T[], keys: K[], query: string, opts?: {sort?: boolean}): T[] {
    if (query.length === 0) return array;

    let k = 3;
    if (query.length < 3) k = 1;

    const profiles: StringSimilarityProfile[] = [];
    for (const elem of array) {
        let builder = "";
        for (const key of keys) {
            const value = elem[key] as any;
            if (builder) builder += " ";
            builder += value;
        }
        const profile = precomputeStringSimilarityProfile(builder.toLowerCase(), k);
        profiles.push(profile);
    }

    let rankings: {idx: number, score: number}[] = [];
    const queryProfile = precomputeStringSimilarityProfile(query.toLowerCase(), k);
    for (let i = 0; i < profiles.length; i++) {
        const profile = profiles[i];
        let score = computeStringSimilarity(queryProfile, profile);
        if (score <= 0.01) continue;
        rankings.push({idx: i, score: score});
    }
    rankings.sort((a, b) => {
        if (a.score < b.score) {
            return 1;
        } else if (a.score > b.score) {
            return -1;
        } else {
            if (a.idx < b.idx) return -1;
            if (a.idx > b.idx) return 1;
            return 0;
        }
    });

    return rankings.map(it => array[it.idx]);
}

export function fuzzyMatch<T, K extends keyof T>(item: T, keys: K[], query: string): boolean {
    const fuse = new Fuse(
        [item],
        {
            threshold: 0.6,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
            shouldSort: false,
            keys: keys as string[]
        }
    );

    return fuse.search(query).length > 0;
}

export function newFuzzyMatchFuse<T, K extends keyof T>(keys: K[]): Fuse<T> {
    return new Fuse(
        [],
        {
            threshold: 0.1,
            minMatchCharLength: 1,
            shouldSort: false,
            keys: keys as string[]
        }
    );
}