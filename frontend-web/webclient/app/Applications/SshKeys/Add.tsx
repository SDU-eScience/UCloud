import * as React from "react";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import SshKeyApi from "@/UCloud/SshKeyApi";
import {Box, Button, Card, Icon, Input, Link, TextArea} from "@/ui-components";
import {useCallback, useMemo, useState} from "react";
import {bulkRequestOf} from "@/UtilityFunctions";
import {callAPI} from "@/Authentication/DataHook";
import {extractErrorMessage, doNothing} from "@/UtilityFunctions";
import {useNavigate} from "react-router-dom";
import * as Heading from "@/ui-components/Heading";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {KeyboardNavigation, SubmitShortcut, useSubmitShortcut} from "@/Applications/KeyboardNavigation";
import {DocumentTypography} from "@/ui-components/Markdown";
import {injectStyle} from "@/Unstyled";

const SshKeyCreateHeaderClass = injectStyle("ssh-key-create-header", key => `
    ${key} {
        display: flex;
        margin: 32px 50px 24px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px;
        }
    }
`);

const SshKeyCreateContentClass = injectStyle("ssh-key-create-content", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 24px;
        max-width: 960px;
        margin: 0 50px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 0 16px;
        }
    }
`);

const SshKeyCreateSubmitClass = injectStyle("ssh-key-create-submit", key => `
    ${key} {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 8px;
        margin: 0 0 48px;
    }

    @media (max-width: 600px) {
        ${key} {
            align-items: stretch;
            flex-direction: column;
        }

        ${key} > div:first-child {
            margin-right: 0 !important;
        }

        ${key} > a, ${key} > button {
            width: 100%;
        }
    }
`);

export function SshKeysCreate(): React.ReactNode {
    usePage(SshKeyApi.titlePlural, SidebarTabId.RESOURCES);

    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [titleError, setTitleError] = useState<string | undefined>(undefined)
    const [contentError, setContentError] = useState<string | undefined>(undefined)

    // NOTE(Dan): We really don't want people sending us their private key, ever. As a result, we do enough checks that
    // we can be pretty confident that their request doesn't contain their private key.
    const validPrefixes = [
        "ecdsa-sha2-nistp256",
        "ecdsa-sha2-nistp384",
        "ecdsa-sha2-nistp521",

        "sk-ecdsa-sha2-nistp256@openssh.com",
        "sk-ssh-ed25519@openssh.com",

        "ssh-ed25519",
        "ssh-rsa",
    ];

    const keyHelp = useMemo(() => {
        return `Must begin with one of the ${validPrefixes.map(it => "`" + it + "`").join(", ")}.

You can learn how to generate an SSH key [here](https://docs.cloud.sdu.dk/hands-on/ssh-login.html).`
    }, []);

    const titleKey = "key-title";
    const contentKey = "key-contents";

    const submit = useCallback(async () => {
        if (loading) return;
        const title = (document.getElementById(titleKey) as HTMLInputElement).value.trim();
        const contents = (document.getElementById(contentKey) as HTMLTextAreaElement).value.trim();

        if (title.length === 0) {
            setTitleError("Title cannot be blank");
            setContentError(undefined);
            return;
        }

        let matchesAny = false;
        for (const prefix of validPrefixes) {
            if (contents.startsWith(prefix)) {
                matchesAny = true;
                break;
            }
        }

        if (!matchesAny) {
            setTitleError(undefined);
            setContentError("Invalid key supplied. Make sure you copy your public key!");
            return;
        }

        if (contents.includes("\n")) {
            setTitleError(undefined);
            setContentError("The public key must not contain any new-lines");
            return;
        }

        try {
            setLoading(true);
            await callAPI(SshKeyApi.create(bulkRequestOf({key: contents, title})))
        } catch (e) {
            setTitleError(undefined);
            setContentError(extractErrorMessage(e as {request: XMLHttpRequest; response: any}));
            return;
        } finally {
            setLoading(false);
        }

        navigate("/ssh-keys");
    }, [loading]);

    useSubmitShortcut(submit, loading);

    return <MainContainer
        main={
            <DocumentTypography>
                <div className={SshKeyCreateHeaderClass}>
                    <div>
                        <Heading.h2>Add SSH key</Heading.h2>
                        <div style={{maxWidth: "960px", color: "var(--textSecondary)"}}>
                            Add a public SSH key that providers can use to authenticate your identity when using
                            SSH enabled services.
                        </div>
                    </div>
                </div>
                <KeyboardNavigation>
                    <div className={SshKeyCreateContentClass}>
                        <Card>
                            <Box p="16px">
                                <FieldGroup>
                                    <FieldRow
                                        title="Title"
                                        description={"Something which will help you remember which key this is. For example: Office PC."}
                                        required
                                        error={titleError}
                                        control={<Input id={titleKey} width="100%" placeholder="Office PC" />}
                                    />
                                    <FieldRow
                                        title="Public key"
                                        description={keyHelp}
                                        required
                                        error={contentError}
                                        control={<TextArea id={contentKey} width="100%" rows={5} placeholder="ssh-ed25519 AAAA..." />}
                                    />
                                </FieldGroup>
                            </Box>
                        </Card>

                        <div className={SshKeyCreateSubmitClass}>
                            <Link to="/ssh-keys">
                                <Button onClick={doNothing} color={"secondaryMain"}>Cancel</Button>
                            </Link>
                            <Button onClick={submit} color={"successMain"} disabled={loading}>
                                {loading ?
                                    <Icon name={"refresh"} spin /> :
                                    <>Add SSH key</>
                                }
                                <SubmitShortcut />
                            </Button>
                        </div>
                    </div>
                </KeyboardNavigation>
            </DocumentTypography>
        }
    />;
};

export default SshKeysCreate;
