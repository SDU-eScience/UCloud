import * as React from "react";
import MainContainer from "@/MainContainer/MainContainer";
import { ResourceTab, ResourceTabOptions } from "@/Resource/ResourceTabs";
import { SidebarPages, useSidebarPage } from "@/ui-components/Sidebar";
import { useTitle } from "@/Navigation/Redux/StatusActions";
import SshKeyApi from "@/UCloud/SshKeyApi";
import { Box, Button, Flex, Icon, Input, Label, Markdown, Text, TextArea } from "@/ui-components";
import { TextP } from "@/ui-components/Text";
import { MandatoryField } from "@/Applications/Jobs/Widgets";
import { useCallback, useMemo, useState } from "react";
import { bulkRequestOf } from "@/DefaultObjects";
import { callAPI } from "@/Authentication/DataHook";
import { extractErrorMessage } from "@/UtilityFunctions";
import { useHistory } from "react-router";

interface GenericInputFieldProps {
    name: string;
    title: string;
    optional?: boolean;
    description?: string;
    onRemove?: () => void;
    error?: string;
}

const GenericInputField: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <Box mt={"1em"}>
        <Label fontSize={1} htmlFor={props.name}>
            <Flex>
                <Flex data-component={"param-title"}>
                    {props.title}
                    {props.optional ? null : <MandatoryField/>}
                </Flex>
                {!props.onRemove ? null : (
                    <>
                        <Box ml="auto"/>
                        <Text color="red" cursor="pointer" mb="4px" onClick={props.onRemove} selectable={false}
                              data-component={"param-remove"}>
                            Remove
                            <Icon ml="6px" size={16} name="close"/>
                        </Text>
                    </>
                )}
            </Flex>
        </Label>
        {props.children}
        {props.error ? <TextP color={"red"}>{props.error}</TextP> : null}
        {props.description ? <Markdown>{props.description}</Markdown> : null}
    </Box>;
}

const GenericTextField: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <GenericInputField {...props}>
        <Input id={props.name}/>
    </GenericInputField>
}

const GenericTextArea: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <GenericInputField {...props}>
        <TextArea width={"100%"} rows={5} id={props.name}/>
    </GenericInputField>
}

export const SshKeysCreate: React.FunctionComponent = () => {
    useTitle(SshKeyApi.titlePlural);
    useSidebarPage(SidebarPages.Resources);

    const history = useHistory();
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
        
You can learn how to generate an SSH key [here](https://docs.hpc-type3.sdu.dk/intro/ssh-login.html#generate-a-new-ssh-key).`
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
            await callAPI(SshKeyApi.create(bulkRequestOf({key: contents, title})))
        } catch (e) {
            setTitleError(undefined);
            setContentError(extractErrorMessage(e));
            return;
        } finally {
            setLoading(false);
        }

        history.push("/ssh-keys");
    }, []);

    return <MainContainer
        header={<ResourceTab active={ResourceTabOptions.SSH_KEYS}/>}
        headerSize={48}
        main={
            <>
                <Box maxWidth={"900px"}>
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

                        <Button type={"submit"} color={"green"} fullWidth>
                            {loading ?
                                <Icon name={"refresh"} spin /> :
                                <>Add SSH key</>
                            }
                        </Button>
                    </form>
                </Box>
            </>
        }
    />;
};

export default SshKeysCreate;
