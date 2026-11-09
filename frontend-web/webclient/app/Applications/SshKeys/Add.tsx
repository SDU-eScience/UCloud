import * as React from "react";
import SshKeyApi from "@/UCloud/SshKeyApi";
import {Box, Button, Flex, Icon} from "@/ui-components";
import {useCallback, useMemo, useState} from "react";
import {bulkRequestOf, stopPropagation} from "@/UtilityFunctions";
import {callAPI} from "@/Authentication/DataHook";
import {extractErrorMessage} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {GenericTextArea, GenericTextField} from "@/UtilityComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {BulkResponse} from "@/UCloud";
import {FindById} from "@/UCloud/ResourceApi";

export function SshKeysCreate({onAdded} : {onAdded: (responses: BulkResponse<FindById>) => void}): React.ReactNode {
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

    const onSubmit = useCallback(async (e) => {
        e.preventDefault();

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
            onAdded(await callAPI<BulkResponse<FindById>>(SshKeyApi.create(bulkRequestOf({ key: contents, title }))));
            dialogStore.success();
        } catch (e: any) {
            setTitleError(undefined);
            setContentError(extractErrorMessage(e));
        } finally {
            setLoading(false);
        }
    }, []);

    return <Box onKeyDown={stopPropagation}>
        <Heading.h3>Add SSH key</Heading.h3>
        <Box>
            <form onSubmit={onSubmit}>
                <GenericTextField
                    name={titleKey}
                    title={"Title"}
                    description={"Something which will help you remember which key this is. For example: Office PC."}
                    error={titleError}
                />
                <GenericTextArea
                    name={contentKey}
                    title={"Public key"}
                    description={keyHelp}
                    error={contentError}
                />

                <Flex justifyContent="end" px={"20px"} py={"12px"} mx={"-20px"} mb={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
                    <Button color={"errorMain"} type="button" onClick={() => dialogStore.failure()}>Cancel</Button>
                    <Button width="120px" color={"successMain"} type="submit">{
                        loading ?
                            <Icon name={"refresh"} spin /> :
                            <>Add SSH key</>
                        }</Button>
                </Flex>
            </form>
        </Box>
    </Box>;
};

export default SshKeysCreate;
