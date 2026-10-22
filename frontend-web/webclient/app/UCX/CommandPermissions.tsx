import * as React from "react";
import {Box, Button, Flex, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {createKeyboardShortcut} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";

export type UcxCommandDecision = "once" | "always" | "deny";

export interface UcxCommandConfirmation {
    scopeId: string;
    command: string;
    jobId: string;
    rank: number;
}

const commandPermissionStorageKey = "ucx.commandPermissions";

type CommandPermissionStore = Record<string, true>;

function readCommandPermissions(): CommandPermissionStore {
    try {
        const raw = window.localStorage.getItem(commandPermissionStorageKey);
        if (raw === null) return {};

        const parsed = JSON.parse(raw) as unknown;
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return {};

        const result: CommandPermissionStore = {};
        for (const [scopeId, allowed] of Object.entries(parsed)) {
            if (allowed === true) result[scopeId] = true;
        }
        return result;
    } catch {
        return {};
    }
}

function writeCommandPermissions(store: CommandPermissionStore): void {
    try {
        window.localStorage.setItem(commandPermissionStorageKey, JSON.stringify(store));
    } catch {
        return;
    }
}

function hasCommandPermission(scopeId: string): boolean {
    return readCommandPermissions()[scopeId] === true;
}

function rememberCommandPermission(scopeId: string): void {
    const store = readCommandPermissions();
    store[scopeId] = true;
    writeCommandPermissions(store);
}

export async function confirmUcxCommand(request: UcxCommandConfirmation): Promise<UcxCommandDecision> {
    if (hasCommandPermission(request.scopeId)) {
        return "always";
    }

    return new Promise<UcxCommandDecision>(resolve => {
        let settled = false;

        const finish = (decision: UcxCommandDecision) => {
            if (settled) return;
            settled = true;

            if (decision === "always") {
                rememberCommandPermission(request.scopeId);
            }

            dialogStore.success();
            resolve(decision);
        };

        dialogStore.addDialog(
            <UcxCommandPermissionDialog
                request={request}
                onDecision={finish}
            />,
            () => {
                if (settled) return;
                settled = true;
                resolve("deny");
            },
            true,
            slimModalStyle,
        );
    });
}

function UcxCommandPermissionDialog(props: {
    request: UcxCommandConfirmation;
    onDecision: (decision: UcxCommandDecision) => void;
}): React.ReactNode {
    const runOnceRef = React.useRef<HTMLButtonElement | null>(null);

    React.useEffect(() => {
        runOnceRef.current?.focus();
    }, []);

    React.useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.repeat) return;
            if (event.key !== "Enter" || event.altKey) return;

            if (event.ctrlKey || event.metaKey) {
                event.preventDefault();
                event.stopPropagation();
                props.onDecision("always");
                return;
            }

            const target = event.target;
            if (target instanceof HTMLElement && target.closest("button, input, textarea, select, a")) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            props.onDecision("once");
        };

        window.addEventListener("keydown", onKeyDown, true);
        return () => window.removeEventListener("keydown", onKeyDown, true);
    }, [props.onDecision]);

    const runOnceShortcut = createKeyboardShortcut("Enter");
    const runAlwaysShortcut = createKeyboardShortcut("Enter", ["ctrl"]);

    return <Box p="20px">
        <Heading.h3>Run command?</Heading.h3>
        <Text mt="8px" color="textSecondary">
            An application is requesting permission to run this command.
        </Text>
        <Text mt="8px">
            Job <span style={{fontFamily: "var(--monospace)"}}>{props.request.jobId}</span>
            {props.request.rank > 0 ? ` (rank ${props.request.rank})` : ""}
        </Text>
        <Box mt="8px">
            <Text bold mb="4px">Command</Text>
            <CodeSnippet maxHeight="240px">{props.request.command}</CodeSnippet>
        </Box>
        <Flex mt="20px" gap="8px" justifyContent="flex-end" flexWrap="wrap">
            <Button color="errorMain" onClick={() => props.onDecision("deny")}>
                Deny
                <ShortcutHint>Esc</ShortcutHint>
            </Button>
            <Button color="successMain" onClick={() => props.onDecision("always")}>
                Always allow
                <ShortcutHint>{runAlwaysShortcut}</ShortcutHint>
            </Button>
            <Button color="primaryMain" btnRef={runOnceRef} onClick={() => props.onDecision("once")}>
                Run once
                <ShortcutHint>{runOnceShortcut}</ShortcutHint>
            </Button>
        </Flex>
    </Box>;
}

function ShortcutHint(props: {children: React.ReactNode}): React.ReactNode {
    return <span className={ShortcutClass} style={{marginLeft: 8}}>{props.children}</span>;
}
