import * as React from "react";
import {Application, ApplicationVariant, deleteApplicationVariant, updateApplicationVariant} from "@/Applications/AppStoreApi";
import {callAPI} from "@/Authentication/DataHook";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {Box, Button, Flex, Icon, Input, Text, Truncate} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ActionEntry, ActionMenu} from "@/ui-components/Actions";
import {extractErrorMessage, doNothing} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";

interface Props {
    flavors: Application[];
    onUpdated(): void | Promise<void>;
    onDeleted?(variant: ApplicationVariant): void;
}

export function openFlavorManagement(flavors: Application[], onUpdated: Props["onUpdated"], onDeleted?: Props["onDeleted"]): void {
    dialogStore.addDialog(<FlavorManagement flavors={flavors} onUpdated={onUpdated} onDeleted={onDeleted} />, () => undefined, true, {
        ...slimModalStyle,
        content: {
            ...slimModalStyle.content,
            width: "min(760px, calc(100vw - 32px))",
            left: "50%",
            transform: "translateX(-50%)",
        },
    });
}

function FlavorManagement({flavors, onUpdated, onDeleted}: Props): React.ReactNode {
    const [items, setItems] = React.useState(() => flavors.flatMap(app => app.metadata.variant ? [app.metadata.variant] : []));
    const [loading, setLoading] = React.useState(false);
    const [renamingId, setRenamingId] = React.useState<number | null>(null);
    const [error, setError] = React.useState<string | null>(null);

    async function update(variant: ApplicationVariant, changes: {title?: string; publishedToProject?: boolean}) {
        if (loading) return;
        setLoading(true);
        setError(null);
        try {
            const updated = await callAPI<ApplicationVariant>(updateApplicationVariant({id: variant.id, ...changes}));
            setItems(current => current.map(item => item.id === updated.id ? updated : item));
            await onUpdated();
        } catch (cause: any) {
            setError("Could not update the flavor. " + extractErrorMessage(cause));
        } finally {
            setLoading(false);
        }
    }

    async function remove(variant: ApplicationVariant) {
        if (loading) return;
        setLoading(true);
        setError(null);
        try {
            await callAPI(deleteApplicationVariant({id: variant.id}));
            setItems(current => current.filter(item => item.id !== variant.id));
            await onUpdated();
            onDeleted?.(variant);
        } catch (cause: any) {
            setError("Could not delete the flavor. " + extractErrorMessage(cause));
        } finally {
            setLoading(false);
        }
    }

    return <div className={Container} onKeyDown={event => {
        if (event.key !== "Escape") event.stopPropagation();
    }}>
        <Heading.h3>Manage your flavors</Heading.h3>
        <div className="flavor-header">
            <Text>Title</Text>
            <Text>Base version</Text>
            <Text>Created by</Text>
            <Text>Published</Text>
            <span />
        </div>
        {items.map(variant => <FlavorRow
            key={variant.id}
            variant={variant}
            loading={loading}
            renaming={renamingId === variant.id}
            startRenaming={() => setRenamingId(variant.id)}
            stopRenaming={() => setRenamingId(null)}
            update={changes => update(variant, changes)}
            remove={() => remove(variant)}
        />)}
        {error ? <Text color="errorMain">{error}</Text> : null}
        <Flex justifyContent="end">
            <Button type="button" disabled={loading} onClick={() => dialogStore.success()}>Close</Button>
        </Flex>
    </div>;
}

function FlavorRow(props: {
    variant: ApplicationVariant;
    loading: boolean;
    renaming: boolean;
    startRenaming(): void;
    stopRenaming(): void;
    update(changes: {title?: string; publishedToProject?: boolean}): Promise<void>;
    remove(): Promise<void>;
}): React.ReactNode {
    const openMenu = React.useRef<(left: number, top: number) => void>(doNothing);
    const renameInput = React.useRef<HTMLInputElement | null>(null);
    const renameFinished = React.useRef(false);

    React.useLayoutEffect(() => {
        if (!props.renaming) return;
        renameFinished.current = false;
        renameInput.current?.focus();
        renameInput.current?.select();
    }, [props.renaming]);

    async function finishRename(value: string, cancel: boolean) {
        if (renameFinished.current) return;
        renameFinished.current = true;
        props.stopRenaming();
        const title = value.trim();
        if (!cancel && title && title !== props.variant.title) await props.update({title});
    }

    const actions: ActionEntry<ApplicationVariant, null>[] = [
        {
            text: "Rename",
            icon: "edit",
            enabled: () => !props.loading,
            onClick: () => props.startRenaming(),
        },
        {
            text: props.variant.publishedToProject ? "Unpublish from project" : "Publish to project",
            icon: props.variant.publishedToProject ? "heroLockClosed" : "heroGlobeAlt",
            enabled: () => !props.loading,
            onClick: () => props.update({publishedToProject: !props.variant.publishedToProject}),
        },
        "divider",
        {
            text: "Delete",
            icon: "heroTrash",
            destructive: true,
            confirmationText: `Delete ${props.variant.title}?`,
            confirmationButtonText: "Delete",
            enabled: () => !props.loading,
            onClick: () => props.remove(),
        },
    ];

    return <>
        <div className="flavor-row" onContextMenu={event => {
            event.preventDefault();
            openMenu.current(event.clientX, event.clientY);
        }}>
            <Box minWidth={0}>
                {props.renaming ? <Input
                    inputRef={renameInput}
                    noBorder
                    defaultValue={props.variant.title}
                    style={{padding: 0, height: "auto", boxShadow: "none", backgroundColor: "transparent"}}
                    onBlur={event => finishRename(event.target.value, false)}
                    onKeyDown={event => {
                        event.stopPropagation();
                        if (event.key === "Enter") finishRename(event.currentTarget.value, false);
                        if (event.key === "Escape") finishRename(event.currentTarget.value, true);
                    }}
                /> : <Truncate title={props.variant.title}>{props.variant.title}</Truncate>}
            </Box>
            <Truncate title={props.variant.baseApplication.version}>{props.variant.baseApplication.version}</Truncate>
            <Truncate title={props.variant.createdBy}>{props.variant.createdBy}</Truncate>
            <Text>{props.variant.publishedToProject ? "Yes" : "No"}</Text>
            <button
                type="button"
                className="flavor-operations"
                aria-label={`Operations for ${props.variant.title}`}
                disabled={props.loading}
                onClick={event => {
                    event.preventDefault();
                    event.stopPropagation();
                    const rect = event.currentTarget.getBoundingClientRect();
                    openMenu.current(rect.left, rect.bottom);
                }}
            >
                <Icon name="ellipsis" size={12} />
            </button>
        </div>
        <ActionMenu
            actions={actions}
            openFnRef={openMenu}
            selected={[props.variant]}
            callbacks={null}
            trigger={null}
        />
    </>;
}

const Container = injectStyle("flavor-management", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 12px;
        max-height: min(760px, calc(100vh - 80px));
        overflow-y: auto;
    }

    ${key} .flavor-header,
    ${key} .flavor-row {
        display: grid;
        grid-template-columns: minmax(180px, 2fr) minmax(100px, 1fr) minmax(140px, 1fr) 90px 32px;
        gap: 16px;
        align-items: center;
        min-height: 40px;
        padding: 0 12px;
    }

    ${key} .flavor-header {
        color: var(--textSecondary);
        border-bottom: 1px solid var(--borderColor);
    }

    ${key} .flavor-row {
        border-radius: 5px;
    }

    ${key} .flavor-row:hover {
        background: var(--rowHover);
    }

    ${key} .flavor-operations {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        padding: 0;
        border: 0;
        border-radius: 999px;
        color: inherit;
        background: transparent;
        cursor: pointer;
    }

    ${key} .flavor-operations:hover {
        background: var(--rowHover);
    }

    @media (max-width: 640px) {
        ${key} .flavor-header,
        ${key} .flavor-row {
            grid-template-columns: minmax(140px, 2fr) minmax(90px, 1fr) minmax(100px, 1fr) 70px 32px;
            gap: 8px;
            font-size: 12px;
        }
    }
`);
