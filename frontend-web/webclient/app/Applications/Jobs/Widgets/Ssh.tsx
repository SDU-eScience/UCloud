import * as React from "react";
import {useEffect, useState} from "react";
import {PageV2} from "@/UCloud";
import {useCloudAPI} from "@/Authentication/DataHook";
import SshKeyApi, {SSHKey} from "@/UCloud/SshKeyApi";
import {Box, Card, Flex, Label, Select} from "@/ui-components";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {Application} from "@/Applications/AppStoreApi";
import {TooltipV2} from "@/ui-components/Tooltip";
import {FieldRow} from "@/Applications/Jobs/Widgets";

export const SshWidget: React.FunctionComponent<{
    application: Application;
    onSshStatusChanged: (enabled: boolean) => void;
    onSshKeysValid: (valid: boolean) => void;
    initialEnabledStatus?: boolean;
    embedded?: boolean;
    fieldRow?: boolean;
}> = props => {
    const sshMode = props.application.invocation.ssh?.mode ?? "DISABLED";
    const [sshKeyFirstPage] = useCloudAPI<PageV2<SSHKey>>(SshKeyApi.browse({itemsPerPage: 10}), emptyPageV2);
    const hasAnyKeys = sshKeyFirstPage.data.items.length > 0;
    const [sshEnabled, setSshEnabled] = useState(false);
    const [tooltipOpen, setTooltipOpen] = useState(false);

    useEffect(() => {
        if (props.initialEnabledStatus !== undefined) setSshEnabled(props.initialEnabledStatus);
    }, [props.initialEnabledStatus]);

    useEffect(() => {
        props.onSshStatusChanged(sshMode === "MANDATORY" || (sshMode === "OPTIONAL" && sshEnabled));
    }, [sshMode, props.onSshStatusChanged, sshEnabled]);

    useEffect(() => {
        props.onSshKeysValid(sshMode !== "MANDATORY" || hasAnyKeys);
        if (!sshKeyFirstPage.loading && !hasAnyKeys && sshMode === "OPTIONAL") setSshEnabled(false);
    }, [hasAnyKeys, props.onSshKeysValid, sshKeyFirstPage.loading, sshMode]);

    useEffect(() => {
        if (!tooltipOpen) return;
        const closeTooltip = () => setTooltipOpen(false);
        document.addEventListener("keydown", closeTooltip);
        document.addEventListener("focusin", closeTooltip);
        return () => {
            document.removeEventListener("keydown", closeTooltip);
            document.removeEventListener("focusin", closeTooltip);
        };
    }, [tooltipOpen]);

    if (sshMode === "DISABLED") return null;
    const disabled = sshMode === "MANDATORY" || sshKeyFirstPage.loading || !hasAnyKeys;
    const tooltip = sshKeyFirstPage.loading ? "Loading your SSH keys..." :
        !hasAnyKeys ? "You do not have any SSH keys configured." :
        sshMode === "MANDATORY" ? "SSH access is required by this application." : undefined;
    const description = "Opens up access to the job via SSH.";
    const selector = <TooltipV2 tooltip={tooltip} disabled={!tooltip} open={disabled ? tooltipOpen : undefined}>
        <div
            onClick={() => {
                if (disabled) setTooltipOpen(true);
            }}
            onMouseLeave={() => setTooltipOpen(false)}
            onBlur={() => setTooltipOpen(false)}
        >
            <Select disabled={disabled} value={(sshMode === "MANDATORY" || sshEnabled).toString()}
                onChange={event => setSshEnabled(event.target.value === "true")}>
                <option value="true">Enabled</option>
                <option value="false">Disabled</option>
            </Select>
        </div>
    </TooltipV2>;
    if (props.fieldRow) {
        return <FieldRow title="SSH access" description={description} control={selector}
            onClear={sshMode === "OPTIONAL" && sshEnabled ? () => setSshEnabled(false) : undefined}
            bold={sshMode === "MANDATORY" || sshEnabled} />;
    }
    const content = <Flex alignItems="center" justifyContent="space-between">
        <Label>SSH access</Label>
        {selector}
    </Flex>;
    return props.embedded ? <Box>{content}</Box> : <Card>{content}</Card>;
};
