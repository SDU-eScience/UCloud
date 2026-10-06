import * as React from "react";
import {useState} from "react";
import {editor} from "monaco-editor";
import {VimMode} from "monaco-vim";
import {Button, Flex, Input, Label, Select} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {addStandardDialog} from "@/UtilityComponents";
import {sendSuccessNotification} from "@/Notifications";

interface StoredSettings {
    fontSize?: number;
    fontWeight?: string;
    wordWrap?: editor.IEditorOptions["wordWrap"];
    vim?: boolean;
}

const settingsKey = "PreviewEditorSettings";
const allowEditingKey = "EDITOR:ALWAYS_ALLOW_EDITING_KEY";
const settingsChanged = "ucloud-editor-settings-changed";

export function getEditorOptions(): StoredSettings {
    const stored = JSON.parse(localStorage.getItem(settingsKey) ?? "{}");
    return {
        fontSize: stored.fontSize === undefined ? 14 : Number(stored.fontSize),
        fontWeight: stored.fontWeight ?? "400",
        wordWrap: stored.wordWrap ?? "off",
        vim: stored.vim === true,
    };
}

export function updateEditorSettings(settings: StoredSettings): void {
    localStorage.setItem(settingsKey, JSON.stringify({...getEditorOptions(), ...settings}));
    window.dispatchEvent(new Event(settingsChanged));
}

export function subscribeEditorSettings(listener: () => void): () => void {
    window.addEventListener(settingsChanged, listener);
    window.addEventListener("storage", listener);
    return () => {
        window.removeEventListener(settingsChanged, listener);
        window.removeEventListener("storage", listener);
    };
}

export function allowEditing(): boolean {
    return localStorage.getItem(allowEditingKey) === "true";
}

export function allowEditDialog(instance: editor.IStandaloneCodeEditor, onReadOnlyChange?: () => void) {
    addStandardDialog({
        title: "Enable editing?",
        message: "Editing files is disabled. This can be changed later in settings. Enable?",
        confirmText: "Enable",
        onConfirm() {
            instance.updateOptions({readOnly: false});
            localStorage.setItem(allowEditingKey, "true");
            onReadOnlyChange?.();
        },
        cancelText: "Dismiss",
        addToFront: true,
    });
}

type VimContext = "insert" | "normal" | "visual";
interface VimBinding {
    lhs: string;
    rhs: string;
    context: VimContext;
}

function getStoredVimKeyBindings(): VimBinding[] {
    return JSON.parse(localStorage.getItem("vim-key-bindings") ?? "[]");
}

export function mapStoredBindings(): void {
    getStoredVimKeyBindings().forEach(binding => {
        if (binding.lhs && binding.rhs) VimMode.Vim.map(binding.lhs, binding.rhs, binding.context);
    });
}

export function MonacoEditorSettings({editor: instance, onReadOnlyChange}: {
    editor: editor.IStandaloneCodeEditor | null;
    onReadOnlyChange?: (readOnly: boolean) => void;
}) {
    const [settings, setSettings] = useState(getEditorOptions);
    React.useEffect(() => subscribeEditorSettings(() => setSettings(getEditorOptions())), []);
    if (!instance) return null;

    return <Flex flexDirection="column" gap="24px">
        <Label>Font size
            <Select value={settings.fontSize ?? 14} onChange={e => updateEditorSettings({fontSize: Number(e.target.value)})}>
                {[8, 10, 12, 14, 16, 18, 20, 22].map(value => <option key={value} value={value}>{value}</option>)}
            </Select>
        </Label>
        <Label>Font weight
            <Select value={settings.fontWeight ?? "400"} onChange={e => updateEditorSettings({fontWeight: e.target.value})}>
                {["200", "400", "600", "800", "bold"].map(value => <option key={value} value={value}>{value}</option>)}
            </Select>
        </Label>
        <Label>Word wrap
            <Select value={settings.wordWrap ?? "off"} onChange={e => updateEditorSettings({wordWrap: e.target.value as StoredSettings["wordWrap"]})}>
                {["wordWrapColumn", "on", "off", "bounded"].map(value => <option key={value} value={value}>{value}</option>)}
            </Select>
        </Label>
        {onReadOnlyChange ? <Label>Allow file editing
            <Select defaultValue={allowEditing() ? "Allow" : "Disallow"} onChange={e => {
                const canEdit = e.target.value === "Allow";
                localStorage.setItem(allowEditingKey, String(canEdit));
                onReadOnlyChange(!canEdit);
            }}>
                <option value="Allow">Allow</option>
                <option value="Disallow">Disallow</option>
            </Select>
        </Label> : null}
        <Label>Vim mode
            <Select value={settings.vim ? "Enabled" : "Disabled"} onChange={e => updateEditorSettings({vim: e.target.value === "Enabled"})}>
                <option value="Enabled">Enabled</option>
                <option value="Disabled">Disabled</option>
            </Select>
        </Label>
        {settings.vim ? <VimKeyBindings /> : null}
    </Flex>;
}

function VimKeyBindings() {
    const [bindings, setBindings] = useState(() => [...getStoredVimKeyBindings(), {lhs: "", rhs: "", context: "normal" as VimContext}]);
    const updateBinding = (index: number, change: Partial<VimBinding>) => {
        setBindings(previous => {
            const next = previous.map((binding, i) => i === index ? {...binding, ...change} : binding);
            const last = next.at(-1);
            if (last?.lhs || last?.rhs) next.push({lhs: "", rhs: "", context: "normal"});
            return next;
        });
    };
    const save = () => {
        for (const context of ["insert", "normal", "visual"] as const) VimMode.Vim.mapclear(context);
        const stored = bindings.filter(binding => binding.lhs && binding.rhs);
        localStorage.setItem("vim-key-bindings", JSON.stringify(stored));
        mapStoredBindings();
        sendSuccessNotification("Bindings updated");
    };
    return <div>
        <Flex>Vim key bindings <Button onClick={save} ml="auto">Save key bindings</Button></Flex>
        <code>:map {"{lhs}"} {"{rhs}"}</code>
        {bindings.map((binding, index) => <Flex key={index} my="12px" gap="4px">
            <Label>Left-hand-side (lhs):
                <Input value={binding.lhs} onChange={e => updateBinding(index, {lhs: e.target.value})} />
            </Label>
            <Label>Right-hand-side (rhs):
                <Input value={binding.rhs} onChange={e => updateBinding(index, {rhs: e.target.value})} />
            </Label>
            <Label>Mode:
                <Select value={binding.context} onChange={e => updateBinding(index, {context: e.target.value as VimContext})}>
                    <option value="normal">normal</option>
                    <option value="insert">insert</option>
                    <option value="visual">visual</option>
                </Select>
            </Label>
            <IconButton tooltip="Remove key binding" icon="close" onClick={() => {
                VimMode.Vim.unmap(binding.lhs, binding.context);
                const next = bindings.filter((_, i) => i !== index);
                if (next.length === 0) next.push({lhs: "", rhs: "", context: "normal"});
                setBindings(next);
                localStorage.setItem("vim-key-bindings", JSON.stringify(next.filter(it => it.lhs && it.rhs)));
            }} />
        </Flex>)}
    </div>;
}
