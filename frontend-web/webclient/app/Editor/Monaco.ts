import {useEffect, useState} from "react";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {populateLanguages} from "@/UtilityFunctions";
import type {languages} from "monaco-editor";

const monacoCache = new AsyncCache<typeof import("monaco-editor")>();

export async function getMonaco() {
    return monacoCache.retrieve("", async () => {
        const monaco = await import("monaco-editor");
        const editorWorker = (await import('monaco-editor/editor/editor.worker?worker')).default;
        const jsonWorker = (await import('monaco-editor/language/json/json.worker?worker')).default;
        const cssWorker = (await import('monaco-editor/language/css/css.worker?worker')).default;
        const htmlWorker = (await import('monaco-editor/language/html/html.worker?worker')).default;
        const tsWorker = (await import('monaco-editor/language/typescript/ts.worker?worker')).default;
        const yamlWorker = (await import('./Yaml.worker?worker')).default;

        populateLanguages(monaco.languages.getLanguages().map(l =>
            ({language: l.id, extensions: l.extensions?.map(it => it.slice(1)) ?? []}))
        );
        self.MonacoEnvironment = {
            getWorker(_workerId, label) {
                switch (label) {
                    case 'yaml': return new yamlWorker();
                    case 'json': return new jsonWorker();
                    case 'css':
                    case 'scss':
                    case 'less': return new cssWorker();
                    case 'html':
                    case 'handlebars':
                    case 'razor': return new htmlWorker();
                    case 'typescript':
                    case 'javascript': return new tsWorker();
                    default: return new editorWorker();
                }
            }
        };
        monaco.editor.defineTheme('ucloud-dark', {
            base: 'vs-dark',
            inherit: true,
            rules: [],
            colors: {'editor.background': '#21262D'}
        });
        monaco.languages.register({id: "jinja2"});
        monaco.languages.setMonarchTokensProvider("jinja2", jinja2monarchTokens);
        return monaco;
    });
}

export function useMonaco(active: boolean): typeof import("monaco-editor") | undefined {
    const [monaco, setMonaco] = useState<typeof import("monaco-editor")>();
    useEffect(() => {
        if (!active) return;
        let cancelled = false;
        getMonaco().then(instance => {
            if (!cancelled) setMonaco(instance);
        });
        return () => { cancelled = true; };
    }, [active]);
    return monaco;
}

export const jinja2monarchTokens: languages.IMonarchLanguage = {
    tokenizer: {
        root: [
            [/\{\{/, 'keyword.control', '@variable'],
            [/\{%/, 'keyword.control', '@statement'],
            [/\{-/, 'keyword.control', '@template'],
            [/\{#/, 'comment.jinja2', '@comment'],
            [/["']/, 'string'],
            [/#.*$/, 'comment'],
        ],
        variable: [[/}}/, 'keyword.control', '@pop'], [/./, 'variable']],
        statement: [[/%}/, 'keyword.control', '@pop'], [/./, 'keyword']],
        comment: [[/#}/, 'comment', '@pop'], [/./, 'comment']],
        template: [[/-}/, 'keyword.control', '@pop'], [/./, 'keyword']],
    }
};
