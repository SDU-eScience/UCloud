function k8sProseMarkdown(text: string): string {
    return text.replace(/(`+)[^\n]*?\1|<([a-zA-Z][\w-]*)>/g, (match, code, placeholder) => code ? match : "`<" + placeholder + ">`");
}

function k8sIsParentheticalProse(text: string): boolean {
    return /^\((?:Note\b|[A-Z][a-z]+(?:\s+[a-zA-Z]+){2})/.test(text) && text.endsWith(")");
}

function k8sIsIndentedProse(lines: string[]): boolean {
    const text = lines.map(line => line.trim()).join(" ");
    if (k8sIsParentheticalProse(text)) return true;
    if (/^(?:type|func|package|import|return|var|const|class|def|if|for|while)\b/.test(text)) return false;
    if (/[{}]|::=|-----BEGIN|-----END/.test(text)) return false;
    return /^[a-zA-Z][a-zA-Z',-]*\s+[a-zA-Z][a-zA-Z',-]*\b/.test(text) && /[.!?)]$/.test(text);
}

function k8sGrammarMarkdown(lines: string[]): string[] {
    const output: string[] = [];
    let code: string[] = [];
    const flush = () => {
        while (code.length > 0 && code[0].trim() === "") code.shift();
        while (code.length > 0 && code.at(-1)?.trim() === "") code.pop();
        if (code.length > 0) output.push("```text", ...code, "```");
        code = [];
    };
    for (const line of lines) {
        const trimmed = line.trim();
        if (k8sIsParentheticalProse(trimmed)) {
            flush();
            if (output.at(-1)?.trim()) output.push("");
            output.push(k8sProseMarkdown(trimmed), "");
        } else {
            code.push(...line.split(/[ \t]+(?=<[a-zA-Z][\w-]*>[ \t]*::=)/));
        }
    }
    flush();
    return output;
}

export function fixK8sApiMarkdownBlocks(markdown: string): string {
    const lines = markdown.split("\n");
    const output: string[] = [];
    let fence: {character: string; length: number} | undefined;
    for (let index = 0; index < lines.length; index++) {
        const line = lines[index];
        const marker = line.match(/^ {0,3}(`{3,}|~{3,})(.*)$/);
        if (fence) {
            const closesFence = marker && marker[1][0] === fence.character
                && marker[1].length >= fence.length && marker[2].trim() === "";
            if (closesFence) fence = undefined;
            output.push(line);
            continue;
        }
        if (marker) {
            const suffix = marker[2].trim();
            const collapsedGrammar = /^<[a-zA-Z][\w-]*>\s*::=/.test(suffix);
            const nextContent = lines.slice(index + 1).find(candidate => candidate.trim() !== "")?.trim() ?? "";
            const fencedGrammar = (suffix === "" || suffix === "text") && /^<[a-zA-Z][\w-]*>\s*::=/.test(nextContent);
            if (collapsedGrammar || fencedGrammar) {
                const content = collapsedGrammar ? [suffix] : [];
                const close = new RegExp("(?:^|[ \\t]+)" + marker[1][0] + "{" + marker[1].length + ",}[ \\t]*$");
                let end = index + 1;
                for (; end < lines.length; end++) {
                    const closing = lines[end].match(close);
                    if (closing) {
                        content.push(lines[end].slice(0, closing.index));
                        break;
                    }
                    content.push(lines[end]);
                }
                if (end < lines.length) {
                    output.push(...k8sGrammarMarkdown(content));
                    index = end;
                    continue;
                }
            }
            fence = {character: marker[1][0], length: marker[1].length};
            output.push(line);
            continue;
        }
        if (!/^(?: {4}|\t)/.test(line)) {
            output.push(line);
            continue;
        }
        const block = [line];
        let end = index + 1;
        while (end < lines.length && /^[ \t]+\S/.test(lines[end])) {
            block.push(lines[end++]);
        }
        const previous = output.findLast(candidate => candidate.trim() !== "") ?? "";
        const nestedList = /^[ \t]*(?:[-+*]|\d+[.)])[ \t]+/.test(previous);
        const list = /^[ \t]+(?:[-+*]|\d+[.)])[ \t]+/.test(line);
        if (list && !nestedList) {
            const indentation = block.map(candidate => candidate.match(/^[ \t]*/)?.[0].replace(/\t/g, "    ") ?? "");
            const base = Math.min(...indentation.map(prefix => prefix.length));
            if (output.at(-1)?.trim()) output.push("");
            output.push(...block.map((candidate, offset) => indentation[offset].slice(base) + candidate.trimStart()));
            if (end < lines.length && lines[end].trim() !== "") output.push("");
        } else if (!nestedList && k8sIsIndentedProse(block)) {
            output.push(...block.map(candidate => k8sProseMarkdown(candidate.trimStart())));
        } else {
            output.push(...block);
        }
        index = end - 1;
    }
    return output.join("\n");
}
