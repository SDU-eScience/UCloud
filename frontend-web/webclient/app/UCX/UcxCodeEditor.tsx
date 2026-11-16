import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {CodeEditor, CodeEditorProps} from "@/Editor/CodeEditor";
import type {editor} from "monaco-editor";
import {yamlFieldLinkEdits, yamlFieldLinks, YamlFieldLink, YamlFieldLinkState} from "@/Editor/YamlFieldLinks";

export function UcxCodeEditor(props: CodeEditorProps & {yamlFieldLinks?: YamlFieldLink[]}) {
    const [draft, setDraft] = useState(props.value ?? "");
    const edited = useRef(false);
    const baseline = useRef(props.value ?? "");
    const instance = useRef<editor.IStandaloneCodeEditor | null>(null);
    const applyingLinks = useRef(false);
    const fieldLinks = useRef<YamlFieldLinkState | undefined>(undefined);
    if (!props.yamlFieldLinks?.length) {
        fieldLinks.current = undefined;
    } else if (!fieldLinks.current || fieldLinks.current.configuration !== JSON.stringify(props.yamlFieldLinks)) {
        fieldLinks.current = yamlFieldLinks(props.schemaRegistry, props.documentId ?? "", draft, props.yamlFieldLinks);
    }
    useEffect(() => {
        if (!edited.current) {
            baseline.current = props.value ?? "";
            setDraft(props.value ?? "");
        }
    }, [props.value]);

    return <CodeEditor
        {...props}
        value={draft}
        dirty={props.onSave ? draft !== baseline.current : false}
        onReady={value => {
            instance.current = value;
            props.onReady?.(value);
        }}
        onChange={value => {
            if (applyingLinks.current) return;
            if (value === draft) return;
            const model = instance.current?.getModel();
            if (fieldLinks.current && model && !props.readOnly) {
                const edits = yamlFieldLinkEdits(fieldLinks.current, value);
                if (edits.length > 0) {
                    applyingLinks.current = true;
                    try {
                        instance.current?.executeEdits("yaml-field-links", edits.map(edit => {
                            const start = model.getPositionAt(edit.start);
                            const end = model.getPositionAt(edit.end);
                            return {
                                range: {startLineNumber: start.lineNumber, startColumn: start.column, endLineNumber: end.lineNumber, endColumn: end.column},
                                text: edit.text,
                            };
                        }));
                        value = model.getValue();
                        fieldLinks.current.source = value;
                    } finally {
                        applyingLinks.current = false;
                    }
                }
            }
            edited.current = true;
            setDraft(value);
            props.onChange?.(value);
        }}
    />;
}
