import * as React from "react";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import SshKeyApi from "@/UCloud/SshKeyApi";
import {Box, Button, Divider, Flex, Icon, Input, Label, Markdown, Text, TextArea} from "@/ui-components";
import {TextP} from "@/ui-components/Text";
import {useCallback, useMemo, useState} from "react";
import {bulkRequestOf} from "@/UtilityFunctions";
import {callAPI} from "@/Authentication/DataHook";
import {extractErrorMessage} from "@/UtilityFunctions";
import {useNavigate} from "react-router";
import * as Heading from "@/ui-components/Heading";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {MandatoryField} from "@/UtilityComponents";

interface GenericInputFieldProps {
    name: string;
    title: string;
    optional?: boolean;
    description?: string;
    onRemove?: () => void;
    error?: string;
    children?: React.ReactNode;
}

const GenericInputField: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <Box mt={"1em"}>
        <Label htmlFor={props.name}>
            <Flex>
                <Flex data-component={"param-title"}>
                    {props.title}
                    {props.optional ? null : <MandatoryField />}
                </Flex>
                {!props.onRemove ? null : (
                    <>
                        <Box ml="auto" />
                        <Text color="errorMain" cursor="pointer" mb="4px" onClick={props.onRemove} selectable={false}
                            data-component={"param-remove"}>
                            Remove
                            <Icon ml="6px" size={16} name="close" />
                        </Text>
                    </>
                )}
            </Flex>
        </Label>
        {props.children}
        {props.error ? <TextP color={"errorMain"}>{props.error}</TextP> : null}
        {props.description ? <Markdown>{props.description}</Markdown> : null}
    </Box>;
}

const GenericTextField: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <GenericInputField {...props}>
        <Input id={props.name} />
    </GenericInputField>
}

const GenericTextArea: React.FunctionComponent<GenericInputFieldProps> = props => {
    return <GenericInputField {...props}>
        <TextArea width={"100%"} rows={5} id={props.name} />
    </GenericInputField>
}

export const SshKeysCreate: React.FunctionComponent = () => {
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

        navigate("/ssh-keys");
    }, []);

    return <MainContainer
        header={<Heading.h2>Add SSH key</Heading.h2>}
        headerSize={48}
        main={
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

                    <Button type={"submit"} color={"successMain"} fullWidth>
                        {loading ?
                            <Icon name={"refresh"} spin /> :
                            <>Add SSH key</>
                        }
                    </Button>
                </form>
            </Box>
        }
    />;
};

const ProviderSupportRow: React.FunctionComponent<{support: {providerId: string; support: string[]}}> = ({support}) => {
    return <TableRow>
        <TableCell width={40}><ProviderLogo providerId={support.providerId} size={32} /></TableCell>
        <TableCell><ProviderTitle providerId={support.providerId} /></TableCell>
        <TableCell>
            {support.support.length > 1 ?
                <ul>
                    {support.support.map((it, idx) => {
                        return <li key={idx}>{it}</li>
                    })}
                </ul> :
                support.support.length === 0 ? "None" : support.support[0]
            }
        </TableCell>
    </TableRow>;
}

// NOTE(Dan): This is hardcoded pending proper support from providers and backend
const hardcodedSshSupport: {providerId: string; support: string[]}[] = [
    {providerId: "ucloud", support: ["SSH to jobs (on-demand)"]},
    {providerId: "aau", support: ["None, keys are added through the application"]},
    {providerId: "hippo", support: ["SSH to frontend"]},
    {providerId: "lumi-sdu", support: []},
];

export default SshKeysCreate;
