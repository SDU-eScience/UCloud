import * as React from "react";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {dialogStore} from "@/Dialog/DialogStore";
import {useProjectId} from "@/Project/Api";
import {sendFailureNotification} from "@/Notifications";
import {bulkRequestOf, doNothing} from "@/UtilityFunctions";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import * as Heading from "@/ui-components/Heading";
import Warning from "@/ui-components/Warning";
import {Box, Button, Divider, Input, Text} from "@/ui-components";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router-dom";

import {callAPI} from "@/Authentication/DataHook";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {dateToString} from "@/Utilities/DateUtilities";
import MainContainer from "@/ui-components/MainContainer";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {
    addProjectSwitcherInPortal,
    EmptyReasonTag,
    ResourceBrowseFeatures,
    ResourceBrowser,
} from "@/ui-components/ResourceBrowser";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {DELETE_TAG, Permission, ResourceAclEntry} from "@/UCloud/ResourceApi";

import * as StackApi from "./api";
import Flex from "@/ui-components/Flex";

const defaultRetrieveFlags = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: false,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

export default function StacksBrowse(): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<StackApi.Stack> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    const projectId = useProjectId();

    usePage("Stacks", SidebarTabId.RUNS);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<StackApi.Stack>(mount, "Stacks", undefined).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "ID"},
                    {name: "Type", columnWidth: 180},
                    {name: "Created at", columnWidth: 180},
                    {name: "", columnWidth: 0},
                ]);

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.stacks.view(resource.id));
                        return;
                    }

                    callAPI(StackApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(StackApi.browse({
                        next: browser.cachedNext[path] ?? undefined,
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                    }));

                    if (path !== browser.currentPath) return;
                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (stack, row) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);
                    ResourceBrowser.icons.renderIcon({
                        name: "heroServerStack",
                        color: "textPrimary",
                        color2: "textPrimary",
                        width: 64,
                        height: 64,
                    }).then(setIcon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(stack.id, row));
                    row.stat1.textContent = stack.type ?? stack.name ?? "Unknown";
                    row.stat2.textContent = dateToString(stack.createdAt);
                });

                browser.on("pathToEntry", stack => stack.id);
                browser.on("generateBreadcrumbs", () => [{title: "Stacks", absolutePath: ""}]);
                browser.on("unhandledShortcut", () => {
                });
                browser.setEmptyIcon("heroServerStack");

                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    isCreating: false,
                    api: {isCoreResource: true},
                    invokeCommand: callAPI,
                    reload: () => browser.refresh(),
                    projectId: projectId,
                }));

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as StackOperationCallbacks;
                    return retrieveOperations().filter(op => op.enabled(selected, callbacks, selected));
                });

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING:
                            e.reason.append("We are fetching your stacks...");
                            break;
                        case EmptyReasonTag.EMPTY:
                            e.reason.append("No stacks found in this workspace.");
                            break;
                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS:
                            e.reason.append("We could not find stacks for this workspace.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        case EmptyReasonTag.UNABLE_TO_FULFILL:
                            e.reason.append("We are currently unable to show your stacks. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                    }
                });
            });
        }

        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    useSetRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />;
}

function retrieveOperations(): Operation<StackApi.Stack, StackOperationCallbacks>[] {
    return [
        {
            text: "Permissions",
            icon: "share",
            enabled: (selected, cb) => {
                if (selected.length !== 1) return false;
                const permissions = selected[0].permissions?.myself ?? [];
                return permissions.includes("ADMIN") && cb.projectId != null;
            },
            onClick: ([stack], cb) => {
                dialogStore.addDialog(
                    <StackPermissionsEditor stack={stack} onDone={() => cb.reload()} />,
                    doNothing,
                    true,
                    slimModalStyle,
                );
            },
            shortcut: ShortcutKey.W,
        },
        {
            text: "Delete",
            icon: "trash",
            color: "errorMain",
            confirm: false,
            tag: DELETE_TAG,
            enabled: selected => selected.length > 0 && selected.every(it => (it.permissions?.myself ?? []).includes("ADMIN")),
            onClick: (selected, cb) => {
                dialogStore.addDialog(
                    <StackDeleteDialog selected={selected} onDeleted={() => cb.reload()} />, doNothing, true,
                );
            },
            shortcut: ShortcutKey.R,
        }
    ];
}

interface StackOperationCallbacks {
    projectId?: string;
    reload: () => void;
}

function StackPermissionsEditor({stack, onDone}: {stack: StackApi.Stack; onDone: () => void}): React.ReactNode {
    const projectId = useProjectId();
    const [acl, setAcl] = React.useState<ResourceAclEntry[]>(stack.permissions?.others ?? []);

    const updateAcl = React.useCallback(async (group: string, permission: Permission | null) => {
        if (!projectId) return;
        const existing = acl.find(it => it.entity.type === "project_group" && it.entity.group === group && it.entity.projectId === projectId);

        const deleted = existing ? [existing.entity] : [];
        const added = permission ? [{
            entity: {type: "project_group" as const, projectId, group},
            permissions: permission === "EDIT" ? ["READ", "EDIT"] as Permission[] : ["READ"] as Permission[],
        }] : [];

        try {
            await callAPI(StackApi.updateAcl(bulkRequestOf({
                id: stack.id,
                added,
                deleted,
            })));
        } catch {
            sendFailureNotification("Failed to update permissions.");
            return;
        }

        const nextAcl = acl.filter(it => {
            if (it.entity.type !== "project_group") return true;
            return !(it.entity.projectId === projectId && it.entity.group === group);
        });

        if (added.length > 0) {
            nextAcl.push(added[0]);
        }

        setAcl(nextAcl);
    }, [acl, projectId, stack.id]);

    return <div onKeyDown={e => e.stopPropagation()}>
        <Heading.h3>Permissions for stack</Heading.h3>
        <Divider />
        <Box mt="8px" mb="12px">
            <Text>
                Control which project groups can use this stack in new jobs.
            </Text>
        </Box>

        {!projectId ? <Text>This operation is only available inside a project workspace.</Text> : null}

        {!projectId ? null : <PermissionsTable
            acl={acl}
            anyGroupHasPermission={acl.some(it => it.permissions.length > 0)}
            showMissingPermissionHelp={false}
            replaceWriteWithUse
            warning="Warning"
            title="Stack"
            updateAcl={updateAcl}
        />}

        <Flex mt={"20px"} justifyContent={"end"}>
            <Button color="primaryMain" onClick={() => {
                onDone();
                dialogStore.success();
            }}>Done</Button>
        </Flex>
    </div>;
}

function StackDeleteDialog({selected, onDeleted}: {selected: StackApi.Stack[]; onDeleted: () => void}): React.ReactNode {
    const singleStack = selected.length === 1;
    const requiredText = singleStack
        ? selected[0].id
        : `I know what I'm doing. Delete the ${selected.length} stacks`;
    const plural = singleStack ? "" : "s";

    return <div onKeyDown={e => e.stopPropagation()}>
        <Heading.h3>Are you absolutely sure?</Heading.h3>
        <Divider />
        <Warning>This is a dangerous operation, please read this!</Warning>
        <Box mb="8px" mt="16px">
            This will <i>PERMANENTLY</i> delete the selected stack{plural}. This action <i>CANNOT BE UNDONE</i>.
        </Box>
        {singleStack ? null : <Box mb="12px">
            <h3>The following stacks will be deleted:</h3>
            <Box mx="16px" maxHeight="116px" overflowY="scroll">
                {selected.map(it => <div key={it.id}>{it.id}</div>)}
            </Box>
        </Box>}
        <Box mb="16px">
            Please type '<b>{requiredText}</b>' to confirm.
        </Box>
        <form onSubmit={async ev => {
            ev.preventDefault();
            ev.stopPropagation();

            const written = (document.querySelector("#stackDeleteName") as HTMLInputElement)?.value;
            if (written !== requiredText) {
                sendFailureNotification(`Please type '${requiredText}' to confirm.`);
                return;
            }

            try {
                await callAPI(StackApi.remove(bulkRequestOf(...selected.map(it => ({id: it.id})))));
                dialogStore.success();
                onDeleted();
            } catch {
                sendFailureNotification("Failed to delete stack(s).");
            }
        }}>
            <Input id="stackDeleteName" autoFocus mb="8px" />
            <Button color="errorMain" type="submit" fullWidth>
                I understand what I am doing, delete permanently
            </Button>
        </form>
    </div>;
}
