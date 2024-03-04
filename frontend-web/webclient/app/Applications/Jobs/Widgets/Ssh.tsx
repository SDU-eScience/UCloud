import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {PageV2} from "@/UCloud";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import SshKeyApi, {SSHKey} from "@/UCloud/SshKeyApi";
import {Card, Checkbox, Label, Link} from "@/ui-components";
import Warning from "@/ui-components/Warning";
import {Feature, hasFeature} from "@/Features";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {Application} from "@/Applications/AppStoreApi";

export const SshWidget: React.FunctionComponent<{
    application: Application;
    onSshStatusChanged: (enabled: boolean) => void;
    onSshKeysValid: (valid: boolean) => void;
    initialEnabledStatus?: boolean;
}> = props => {
    if (!hasFeature(Feature.SSH)) return null;

    const sshMode = props.application.invocation.ssh?.mode ?? "DISABLED";
    const [sshKeyFirstPage] = useCloudAPI<PageV2<SSHKey>>(SshKeyApi.browse({itemsPerPage: 10}), emptyPageV2);
    let hasAnyKeys = sshKeyFirstPage.data.items.length > 0;
    const [sshEnabled, setSshEnabled] = useState(false);

    useEffect(() => {
        if (props.initialEnabledStatus !== undefined) setSshEnabled(props.initialEnabledStatus);
    }, [props.initialEnabledStatus]);

    useEffect(() => {
        props.onSshStatusChanged(sshMode === "MANDATORY" || (sshMode === "OPTIONAL" && sshEnabled));
    }, [sshMode, props.onSshStatusChanged, sshEnabled]);

    const toggleSshEnabled = useCallback(() => {
        setSshEnabled(prev => !prev);
    }, []);

    if (sshMode === "DISABLED") return null;
    return <Card>
        <Heading.h4 mb={10}>Configure SSH access</Heading.h4>

        {sshMode !== "MANDATORY" ? null : <>
            <p>
                This application has been configured to use SSH. 
            </p>

            {hasAnyKeys ? null : <>
                <Warning>
                    <p>
                        You don't currently have any keys configured. You must upload at least one SSH key to start
                        this application.
                    </p>

                    <p>
                        You can configure your SSH keys{" "}
                        <Link to={"/ssh-keys"} target={"_blank"}>here</Link>.
                    </p>
                </Warning>
            </>}
        </>}

        {sshMode !== "OPTIONAL" ? null : <>
            <p>
                This application has optional support for SSH. In order to use SSH access, you must configure at least
                one SSH key. You can configure your SSH keys{" "}
                <Link to={"/ssh-keys"} target={"_blank"}>here</Link>.
            </p>

            {hasAnyKeys ? <>
                <Label mt={16}>
                    <Checkbox checked={sshEnabled} onChange={toggleSshEnabled} size={28}/>
                    Enable SSH server
                </Label>
            </> : <>
                <Warning>
                    You don't currently have any keys configured. You must upload at least one SSH key to use SSH.
                </Warning>
            </>}
        </>}
    </Card>;
};
