import {Client} from "@/Authentication/HttpClientInstance";
import {KeyCode} from "@/DefaultObjects";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import * as Heading from "@/ui-components/Heading";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import Button from "./Button";
import ClickableDropdown from "./ClickableDropdown";
import ExternalLink from "./ExternalLink";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Label from "./Label";
import Radio from "./Radio";
import Text from "./Text";
import {Spacer} from "./Spacer";
import {TextDiv, TextSpan} from "./Text";
import TextArea from "./TextArea";
import {apiUpdate, useCloudCommand} from "@/Authentication/DataHook";
import Error from "./Error";

const enum SupportType {
    SUGGESTION = "SUGGESTION",
    BUG = "BUG"
}

type SystemStatus = "Decomissioned\n" | "Operational\n" | "Degraded\n" | "Down\n";

function submitTicket(request: {subject: string, message: string}): APICallParameters {
    return apiUpdate(request, "/api/support", "ticket")
}

export default function Support(): JSX.Element {
    const textArea = useRef<HTMLTextAreaElement>(null);
    const titleArea = useRef<HTMLTextAreaElement>(null);
    const [type, setType] = useState(SupportType.SUGGESTION);
    const [loading, invokeCommand] = useCloudCommand();
    const [statusUCloud, setUCloudStatus] = useState<SystemStatus | "">("");

    async function onSubmit(event: React.FormEvent): Promise<void> {
        event.preventDefault();
        const text = textArea.current?.value ?? "";
        const title = titleArea.current?.value ?? "";
        if (text.trim()) {
            try {
                await invokeCommand(submitTicket({subject: title, message: `${type}: ${text}`}));
                textArea.current!.value = "";
                titleArea.current!.value = "";
                snackbarStore.addSuccess("Support ticket submitted!", false);
            } catch (e) {
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred submitting the message"), false);
            }
        } else {
            snackbarStore.addFailure("Support message can't be empty.", false);
        }
    }

    const fetchStatus = React.useCallback(() => {
        const controller = new AbortController();
        fetch("https://status.cloud.sdu.dk/health/", {signal: controller.signal}).then(it =>
            it.text().then(it => setUCloudStatus(it as SystemStatus)).catch(e => console.warn(e))
        ).catch(e => console.warn(e));
        return controller;
    }, []);

    React.useEffect(() => {
        const controller = fetchStatus();
        return () => {
            controller.abort();
        }
    }, []);

    return (
        <ClickableDropdown
            colorOnHover={false}
            keepOpenOnClick
            onTriggerClick={fetchStatus}
            trigger={(
                <Flex width="48px" justifyContent="center">
                    <Icon name={"chat"} size="18px" color="headerIconColor" color2="headerBg" />
                </Flex>
            )}
            width="650px"
            left="calc(var(--sidebarWidth) - 6px)"
            bottom="-135px"
        >
            <div>
                <Box width="100%" pr={"16px"} color="text">
                    <Spacer alignItems="center"
                        left={<Heading.h3>Support Form</Heading.h3>}
                        right={<>
                            {!CONF.SITE_FAQ_URL ? null : (
                                <ExternalLink href={CONF.SITE_FAQ_URL}>
                                    <Flex>
                                        <b style={{fontSize: "24px", marginRight: ".5em"}}>?</b>
                                        <Text mt="5px" mr="0.8em">FAQ</Text>
                                    </Flex>
                                </ExternalLink>
                            )}
                            {!CONF.SITE_DOCUMENTATION_URL ? null : (
                                <ExternalLink href={CONF.SITE_DOCUMENTATION_URL}>
                                    <Icon name="docs" mr=".5em" />Documentation
                                </ExternalLink>
                            )}
                        </>}
                    />

                    {["Operational\n", ""].includes(statusUCloud) ? null : (<Box my="6px">
                        <Error error={<>One or more systems are experiencing issues. Go to <ExternalLink style={{color: "var(--textHighlight)"}} href="https://status.cloud.sdu.dk">status.cloud.sdu.dk</ExternalLink> for more info.</>} />
                    </Box>)}

                    <Flex mt="8px">
                        <Label>
                            <Radio
                                checked={type === SupportType.SUGGESTION}
                                onChange={setSuggestion}
                            />
                            <Icon name="chat" color2="white" size="1.5em" mr=".5em" />
                            Suggestion
                        </Label>
                        <Label>
                            <Radio
                                checked={type === SupportType.BUG}
                                onChange={setBug}
                            />
                            <Icon name="bug" size="1.5em" mr=".5em" />
                            Bug
                        </Label>
                    </Flex>

                    <form onSubmit={onSubmit}>
                        <TextDiv mt="10px"> Subject </TextDiv>
                        <TextArea width="100%" ref={titleArea} rows={1} />
                        <TextDiv mt="10px">
                            {type === SupportType.BUG ? "Describe your problem below and we will investigate it." :
                                "Describe your suggestion and we will look into it."
                            }
                        </TextDiv>
                        <TextArea width="100%" ref={textArea} rows={6} />
                        <Button
                            mt="6px"
                            fullWidth
                            type="submit"
                            disabled={loading}
                        >
                            <Icon name="mail" size="1.5em" mr=".5em" color="white" color2="midGray" />
                            <TextSpan fontSize={2}>Send</TextSpan>
                        </Button>
                    </form>
                </Box>
            </div>
        </ClickableDropdown>
    );

    function setBug(): void {
        setType(SupportType.BUG);
    }

    function setSuggestion(): void {
        setType(SupportType.SUGGESTION);
    }
}
