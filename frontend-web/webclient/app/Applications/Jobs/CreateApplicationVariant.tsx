import * as React from "react";
import {Box, Button, Flex, Input, Label, Select, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {Toggle} from "@/ui-components/Toggle";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {BackgroundTask, taskStore} from "@/Services/BackgroundTasks/BackgroundTask";
import {extractErrorMessage} from "@/UtilityFunctions";
import {CreateApplicationVariantRequest, Job} from "@/UCloud/JobsApi";
import {injectStyle} from "@/Unstyled";
import * as AppStore from "@/Applications/AppStoreApi";
import {useCloudAPI} from "@/Authentication/DataHook";
import {ApplicationVariant} from "@/Applications/AppStoreApi";
import {Client} from "@/Authentication/HttpClientInstance";
import {checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";

interface Props {
    job: Job;
    submit(request: CreateApplicationVariantRequest): Promise<BackgroundTask | null>;
}

export function openCreateApplicationVariant(
    job: Job,
    submit: Props["submit"],
): void {
    dialogStore.addDialog(
        <CreateApplicationVariant job={job} submit={submit} />,
        () => undefined,
        true,
        slimModalStyle,
    );
}

function CreateApplicationVariant({job, submit}: Props): React.ReactNode {
    const [title, setTitle] = React.useState("");
    const [rank, setRank] = React.useState(0);
    const [publishedToProject, setPublishedToProject] = React.useState(false);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);
    const [target, setTarget] = React.useState<ApplicationVariant | null>(null);
    const [confirmTarget, setConfirmTarget] = React.useState<ApplicationVariant | null>(null);
    const resolvedApplication = job.status.resolvedApplication;
    const [group] = useCloudAPI(
        resolvedApplication ? AppStore.findGroupByApplication({
            appName: resolvedApplication.metadata.name,
            appVersion: resolvedApplication.metadata.version,
            flags: {includeApplications: true, includeVersions: true},
        }) : {noop: true},
        null,
    );
    const suggestions = (group.data?.status.applications ?? []).flatMap(app => {
        const variant = app.metadata.variant;
        return variant && variant.provider === job.specification.product.provider &&
        (variant.createdBy === Client.username || (variant.publishedToProject && checkIsWorkspaceAdmin())) ? [variant] : [];
    });
    const matchingSuggestions = suggestions.filter(variant =>
        !title.trim() || variant.title.toLowerCase().includes(title.trim().toLowerCase())
    );
    const jobBaseApplication = resolvedApplication?.metadata.variant?.baseApplication ?? resolvedApplication?.metadata;

    return <form className={Container} onSubmit={async event => {
        event.preventDefault();
        event.stopPropagation();
        const normalizedTitle = title.trim();
        if (!normalizedTitle || loading) return;
        if (target && confirmTarget?.id !== target.id) {
            setConfirmTarget(target);
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const task = await submit({
                jobId: job.id,
                rank,
                title: normalizedTitle,
                publishedToProject,
                targetVariantId: target?.id,
            });
            if (task) {
                taskStore.addTask(task);
                dialogStore.success();
            }
        } catch (cause: any) {
            setError("Could not save the flavor. " + extractErrorMessage(cause));
        } finally {
            setLoading(false);
        }
    }} onKeyDown={event => {
        if (event.key !== "Escape") event.stopPropagation();
    }}>
        <Box>
            <Heading.h3>Save as flavor</Heading.h3>
            <Text mt="8px">
                A flavor saves installed software and other changes from this job. Use it to <b>save time
                when you need the same dependencies again</b>.
            </Text>
            <Text mt="12px">
                Files and changes in <code>/work</code> are <b>not included</b>.
            </Text>
            <Text mt="12px">
                UCloud <b>pauses the job</b> while it saves the flavor. You cannot use the job during this
                process. It usually takes a few minutes.
            </Text>
        </Box>

        {suggestions.length === 0 ? null : <Box>
            <Text mb="6px">Push to existing flavor</Text>
            <Text color="textSecondary" mb="8px">
                Select a flavor to push a new version. Type a title below to filter this list.
            </Text>
            <div className="flavor-suggestions">
                {matchingSuggestions.map(variant => <button
                    key={variant.id}
                    type="button"
                    data-selected={target?.id === variant.id}
                    onClick={() => {
                        setTitle(variant.title);
                        setTarget(variant);
                        setConfirmTarget(null);
                        setPublishedToProject(variant.publishedToProject);
                    }}
                >
                    <span>{variant.title}</span>
                    <span>Base {variant.baseApplication.version}</span>
                </button>)}
                {matchingSuggestions.length === 0 ? <Text color="textSecondary" p="8px">No matching flavors</Text> : null}
            </div>
        </Box>}

        <Label>
            Flavor title
            <Input
                autoFocus
                value={title}
                placeholder="RStudio with course packages"
                onChange={event => {
                    setTitle(event.target.value);
                    setTarget(null);
                    setConfirmTarget(null);
                }}
            />
            {target ? <Text color="textSecondary" mt="6px">
                This will push a new version to the existing flavor. Its title and publish state will not change.
            </Text> : null}
        </Label>

        {job.specification.replicas <= 1 ? null : <Label>
            Container replica
            <Select value={rank} onChange={event => setRank(Number(event.target.value))}>
                {Array.from({length: job.specification.replicas}, (_, index) =>
                    <option key={index} value={index}>Node {index + 1}</option>
                )}
            </Select>
        </Label>}

        <Flex gap="24px" alignItems="start">
            <Box flexGrow={1} minWidth={0} py="6px">
                <Label>Available to all project members</Label>
                <Text color="textSecondary" mt="3px" fontSize="13px">
                    If disabled, only you can run this flavor. Project admins can still manage it.
                </Text>
            </Box>
            <Box flexShrink={0} pt="12px">
                <Toggle
                    checked={publishedToProject}
                    disabled={target !== null}
                    onChange={checked => setPublishedToProject(!checked)}
                />
            </Box>
        </Flex>

        {confirmTarget ? <Box p="12px" borderRadius="6px" background="var(--warningMain)" color="warningContrast">
            Push a new version to <b>{confirmTarget.title}</b>? The existing versions will remain available.
            {!jobBaseApplication || jobBaseApplication.version === confirmTarget.baseApplication.version ? null : <Text mt="8px">
                This job uses base version {jobBaseApplication.version}, but the flavor uses base version
                {" "}{confirmTarget.baseApplication.version}. The flavor base will change to version {jobBaseApplication.version}.
            </Text>}
        </Box> : null}
        {error ? <Text color="errorMain">{error}</Text> : null}

        <Flex justifyContent="end" px="20px" py="12px" margin="0 -20px -20px" background="var(--dialogToolbar)" gap="8px">
            <Button type="button" color="errorMain" disabled={loading} onClick={() => dialogStore.failure()}>
                Cancel
            </Button>
            <Button type="submit" color="successMain" disabled={loading || !title.trim()}>
                {loading ? "Saving..." : confirmTarget ? "Confirm push" : target ? "Push new version" : "Save"}
            </Button>
        </Flex>
    </form>;
}

const Container = injectStyle("application-variant-creation-container", key => `
    ${key} {
        display: flex;
        gap: 24px;
        flex-direction: column;
    }

    ${key} .flavor-suggestions {
        max-height: 180px;
        overflow-y: auto;
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        background: var(--backgroundDefault);
    }

    ${key} .flavor-suggestions button {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        width: 100%;
        padding: 8px 12px;
        border: 0;
        color: var(--textPrimary);
        background: transparent;
        cursor: pointer;
        text-align: left;
    }

    ${key} .flavor-suggestions button:hover,
    ${key} .flavor-suggestions button[data-selected="true"] {
        background: var(--rowHover);
    }

    ${key} .flavor-suggestions button span:last-child {
        color: var(--textSecondary);
        white-space: nowrap;
    }
`);
