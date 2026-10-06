import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {CodeEditor, CodeEditorProps} from "@/Editor/CodeEditor";

export function UcxCodeEditor(props: CodeEditorProps) {
    const [draft, setDraft] = useState(props.value ?? "");
    const edited = useRef(false);
    useEffect(() => {
        if (!edited.current) setDraft(props.value ?? "");
    }, [props.value]);

    return <CodeEditor
        {...props}
        value={draft}
        onChange={value => {
            if (value === draft) return;
            edited.current = true;
            setDraft(value);
            props.onChange?.(value);
        }}
    />;
}
