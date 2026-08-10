import * as React from "react";
import {Box, Button, Checkbox, Flex, Input, Label, Select, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {BackgroundTask, taskStore} from "@/Services/BackgroundTasks/BackgroundTask";
import {extractErrorMessage} from "@/UtilityFunctions";
import {CreateApplicationVariantRequest, Job} from "@/UCloud/JobsApi";
import {injectStyle} from "@/Unstyled";

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

    return <form className={Container} onSubmit={async event => {
        event.preventDefault();
        event.stopPropagation();
        const normalizedTitle = title.trim();
        if (!normalizedTitle || loading) return;
        setLoading(true);
        setError(null);
        try {
            const task = await submit({
                jobId: job.id,
                rank,
                title: normalizedTitle,
                publishedToProject,
            });
            if (task) {
                taskStore.addTask(task);
                dialogStore.success();
            }
        } catch (cause: any) {
            setError("Could not save the application variant. " + extractErrorMessage(cause));
        } finally {
            setLoading(false);
        }
    }} onKeyDown={event => {
        if (event.key !== "Escape") event.stopPropagation();
    }}>
        <Box>
            <Heading.h3>Save as application variant</Heading.h3>
            <Text mt="8px">
                An application variant saves installed software and other changes from this job. Use it to <b>save time
                when you need the same dependencies again</b>.
            </Text>
            <Text mt="12px">
                Files and changes in <code>/work</code> are <b>not included</b>.
            </Text>
            <Text mt="12px">
                UCloud <b>pauses the job</b> while it saves the application variant. You cannot use the job during this
                process. It usually takes a few minutes.
            </Text>
        </Box>

        <Label>
            Variant title
            <Input
                autoFocus
                value={title}
                placeholder="For example, RStudio with course packages"
                onChange={event => setTitle(event.target.value)}
            />
        </Label>

        {job.specification.replicas <= 1 ? null : <Label>
            Container replica
            <Select value={rank} onChange={event => setRank(Number(event.target.value))}>
                {Array.from({length: job.specification.replicas}, (_, index) =>
                    <option key={index} value={index}>Node {index + 1}</option>
                )}
            </Select>
        </Label>}

        <Box>
            <Label>
                <Checkbox
                    checked={publishedToProject}
                    onChange={event => setPublishedToProject(event.target.checked)}
                />
                Available to all project members
            </Label>
            <Text color="textSecondary" mt="6px">
                If disabled, only you can run this variant. Project administrators can still manage it.
            </Text>
        </Box>

        {error ? <Text color="errorMain">{error}</Text> : null}

        <Box />

        <Flex justifyContent="end" px="20px" py="12px" margin="-20px" background="var(--dialogToolbar)" gap="8px">
            <Button type="button" color="errorMain" disabled={loading} onClick={() => dialogStore.failure()}>
                Cancel
            </Button>
            <Button type="submit" color="successMain" disabled={loading || !title.trim()}>
                {loading ? "Saving..." : "Save"}
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
`);
