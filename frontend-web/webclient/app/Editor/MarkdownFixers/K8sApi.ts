import {fixK8sApiMarkdownBlocks} from "./K8sApiBlocks";

type K8sListMarker = {
    start: number;
    dash: number;
    end: number;
    family: string;
    field?: string;
};

function k8sListHead(text: string): {family: string; field?: string} {
    const field = text.match(/^[a-zA-Z_]\w*(?:\[(?:\*|\d+)\])?(?:\.[a-zA-Z_]\w*(?:\[(?:\*|\d+)\])?)+(?![\w.])/);
    if (field) return {family: "field", field: field[0]};
    const label = /^(?:'[^'\n]+'|"[^"\n]+"|`[^`\n]+`|[a-zA-Z_]\w*)(?:\s*\([^\n)]*\))?\s*(?::| - |\bmeans\b|\bescapes\b)/.test(text);
    if (label) return {family: "label"};
    const sentence = /^(?:[A-Z][a-z]+\b|`[^`\n]+`\s+(?:must|should|is|will)\b)/.test(text);
    if (sentence) return {family: "sentence"};
    if (/^\d+(?:\.\d+)?[a-zA-Z]*\s+(?:will|is|means)\b/.test(text)) return {family: "sentence"};
    if (/^(?:no object|such an object)\b/.test(text)) return {family: "case"};
    return {family: "unknown"};
}

function k8sFixMarkdownLine(line: string): string {
    const protectedRanges = Array.from(line.matchAll(/(`+)[^\n]*?\1|\[[^\]\n]*\]\([^\n)]*\)|\[[^\]\n]*\]\[[^\]\n]*\]|(?:https?:\/\/|mailto:)\S+/g), match => ({
        start: match.index,
        end: match.index + match[0].length,
    }));
    const markers: K8sListMarker[] = [];
    for (const match of line.matchAll(/(^|[ \t]+)-[ \t]*/g)) {
        const dash = match.index + match[1].length;
        if (protectedRanges.some(range => dash >= range.start && dash < range.end)) continue;
        const end = match.index + match[0].length;
        const head = k8sListHead(line.slice(end));
        const missingSpace = end === dash + 1;
        if (missingSpace && head.family !== "field") continue;
        markers.push({start: match.index, dash, end, ...head});
    }
    const edits: {start: number; end: number; replacement: string}[] = [];
    for (let index = 0; index < markers.length; index++) {
        const first = markers[index];
        const prefix = line.slice(0, first.start);
        const startsLine = prefix.trim() === "";
        const followsColon = prefix.trimEnd().endsWith(":");
        if ((!startsLine && !followsColon) || first.family === "unknown") continue;
        const group = [first];
        for (let next = index + 1; next < markers.length; next++) {
            const marker = markers[next];
            if (marker.family !== first.family) continue;
            group.push(marker);
        }
        if (group.length < 2 && first.family !== "field") continue;
        const indent = startsLine ? line.slice(0, first.dash) : "";
        for (const [itemIndex, marker] of group.entries()) {
            const separator = itemIndex === 0 && startsLine ? indent : (itemIndex === 0 ? "\n\n" : "\n" + indent);
            const field = marker.field;
            edits.push({
                start: marker.start,
                end: field ? marker.end + field.length : marker.end,
                replacement: separator + "- " + (field ? "`" + field + "`" : ""),
            });
        }
        break;
    }
    let result = line;
    for (const edit of edits.reverse()) {
        result = result.slice(0, edit.start) + edit.replacement + result.slice(edit.end);
    }
    return result;
}

export function fixK8sApiMarkdown(markdown: string): string {
    let fence: {character: string; length: number} | undefined;
    return fixK8sApiMarkdownBlocks(markdown).split("\n").map(line => {
        const marker = line.match(/^ {0,3}(`{3,}|~{3,})(.*)$/);
        if (fence) {
            const closesFence = marker && marker[1][0] === fence.character
                && marker[1].length >= fence.length && marker[2].trim() === "";
            if (closesFence) fence = undefined;
            return line;
        }
        if (marker) {
            fence = {character: marker[1][0], length: marker[1].length};
            return line;
        }
        if (/^(?: {4}|\t)/.test(line)) return line;
        if (/^ {0,3}\[[^\]]+\]:/.test(line)) return line;
        if (line.includes("|")) return line;
        return k8sFixMarkdownLine(line);
    }).join("\n");
}
