export type StringSimilarityProfile = Record<string, number>;

export function precomputeStringSimilarityProfile(input: string, k: number = 3): StringSimilarityProfile {
    const shingles: Record<string, number> = {};
    for (let i = 0; i < input.length - k + 1; i++) {
        const shingle = input.substring(i, i + k);
        const old = shingles[shingle];
        if (old === undefined) {
            shingles[shingle] = 1;
        } else {
            shingles[shingle] = old + 1;
        }
    }
    return shingles;
}

function dotProduct(a: StringSimilarityProfile, b: StringSimilarityProfile): number {
    const aEntries = Object.entries(a);
    const bEntries = Object.entries(b);

    let small = a;
    let large = b;

    if (bEntries.length < aEntries.length) {
        large = a;
        small = b;
    }

    let agg = 0.0;
    for (const [key, value] of Object.entries(small)) {
        const i = large[key];
        if (i !== undefined) {
            agg += value * i;
        }
    }

    return agg;
}

function norm(profile: StringSimilarityProfile): number {
    let agg = 0.0;
    for (const [k, v] of Object.entries(profile)) {
        agg += v * v;
    }
    return Math.sqrt(agg);
}

export function computeStringSimilarity(a: StringSimilarityProfile, b: StringSimilarityProfile): number {
    return dotProduct(a, b) / (norm(a) * norm(b));
}
