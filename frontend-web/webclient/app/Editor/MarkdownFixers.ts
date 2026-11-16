import {fixK8sApiMarkdown} from "./MarkdownFixers/K8sApi";

const markdownFixers: Record<string, (markdown: string) => string> = {
    "k8s-api": fixK8sApiMarkdown,
};

export function fixEditorMarkdown(markdown: string, fixer?: string): string {
    const fix = fixer && Object.hasOwn(markdownFixers, fixer) ? markdownFixers[fixer] : undefined;
    return fix ? fix(markdown) : markdown;
}
