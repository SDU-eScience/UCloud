import * as React from "react";
import {useRef, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import * as Heading from "@/ui-components/Heading";
import {doNothing, errorMessageOrDefault} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import Button from "./Button";
import ClickableDropdown from "./ClickableDropdown";
import ExternalLink from "./ExternalLink";
import Flex from "./Flex";
import Icon from "./Icon";
import Label from "./Label";
import Radio from "./Radio";
import Text from "./Text";
import {Spacer} from "./Spacer";
import TextArea from "./TextArea";
import {apiUpdate, useCloudCommand} from "@/Authentication/DataHook";
import Error from "./Error";
import Input from "./Input";

const enum SupportType {
    SUGGESTION = "SUGGESTION",
    BUG = "BUG"
}

type SystemStatus = "Decomissioned\n" | "Operational\n" | "Degraded\n" | "Down\n";

function submitTicket(request: {subject: string, message: string}): APICallParameters {
    return apiUpdate(request, "/api/support", "ticket")
}

export default function Support(): React.ReactNode {
    const [textArea, setTextArea] = useState("");
    const [titleArea, setTitleArea] = useState("");
    const [type, setType] = useState(SupportType.SUGGESTION);
    const [loading, invokeCommand] = useCloudCommand();
    const [statusUCloud, setUCloudStatus] = useState<SystemStatus | "">("");

    async function onSubmit(event: React.FormEvent): Promise<void> {
        event.preventDefault();
        const text = textArea;
        const title = titleArea;
        if (text.trim()) {
            try {
                await invokeCommand(submitTicket({subject: title, message: `${type}: ${text}`}));
                setTextArea("");
                setTitleArea("");
                snackbarStore.addSuccess("Support ticket submitted!", false);
                closeRef.current();
            } catch (e) {
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred submitting the message"), false);
            }
        } else {
            snackbarStore.addFailure("Support message can't be empty.", false);
        }
    }

    const closeRef = useRef(() => void 0);

    const fetchStatus = React.useCallback(() => {
        const controller = new AbortController();
        fetch("https://status.cloud.sdu.dk/health/", {signal: controller.signal}).then(it =>
            it.text().then(it => setUCloudStatus(it as SystemStatus)).catch(e => console.warn(e))
        ).catch(doNothing);
        return controller;
    }, []);

    React.useEffect(() => {
        const controller = fetchStatus();

        function closeOnEscapeDown(e: KeyboardEvent) {
            if (e.key === "Escape") {
                closeRef.current();
            }
        }

        window.addEventListener("keydown", closeOnEscapeDown);
        return () => {
            controller.abort();
            window.removeEventListener("keydown", closeOnEscapeDown);
        }
    }, []);

    return (
        <ClickableDropdown
            colorOnHover={false}
            keepOpenOnClick
            closeFnRef={closeRef}
            onTriggerClick={fetchStatus}
            trigger={(
                <Flex width="48px" justifyContent="center">
                    <Icon name={"heroChatBubbleLeftEllipsis"} size="24px" color="fixedWhite" />
                </Flex>
            )}
            width="650px"
            left="calc(var(--sidebarWidth))"
            bottom="-60px"
        >
            <div style={{cursor: "default"}}>
                <Box width="100%" p="16px" color="text" onKeyDown={e => e.stopPropagation()}>
                    <Spacer alignItems="center"
                        left={<Heading.h3>Support Form</Heading.h3>}
                        right={<>
                            {!CONF.SITE_FAQ_URL ? null : (
                                <ExternalLink href={CONF.SITE_FAQ_URL}>
                                    <Flex>
                                        <b style={{fontSize: "24px", marginRight: ".5em"}}>?</b>
                                        <Text mt="8px" mr="0.8em">FAQ</Text>
                                    </Flex>
                                </ExternalLink>
                            )}
                            {!CONF.SITE_DOCUMENTATION_URL ? null : (
                                <ExternalLink hoverColor={"primaryLight"} href={CONF.SITE_DOCUMENTATION_URL}>
                                    <Icon name="heroBookOpen" mr=".5em" />Documentation
                                </ExternalLink>
                            )}
                        </>}
                    />

                    {["Operational\n", ""].includes(statusUCloud) ? null : (<Box my="6px">
                        <Error error={<>One or more systems are experiencing issues. See <ExternalLink href="https://status.cloud.sdu.dk">status.cloud.sdu.dk</ExternalLink> for more info.</>} />
                    </Box>)}

                    <Flex mt="8px" gap={"8px"}>
                        <Label cursor="pointer" width={"initial"}>
                            <Radio
                                checked={type === SupportType.SUGGESTION}
                                onChange={setSuggestion}
                            />
                            <Icon name="heroChatBubbleLeftEllipsis" size="1.5em" mr=".5em" />
                            Suggestion
                        </Label>
                        <Label cursor="pointer" width={"initial"}>
                            <Radio
                                checked={type === SupportType.BUG}
                                onChange={setBug}
                            />
                            <Icon name="bug" size="1.5em" mr=".5em" />
                            Bug
                        </Label>
                    </Flex>

                    <form onSubmit={onSubmit} style={{display: "flex", flexDirection: "column", gap: "8px", marginTop: "8px"}}>
                        <Label>
                            Subject
                            <Input width="100%" value={titleArea} onChange={e => setTitleArea(e.target.value)} />
                        </Label>

                        <Label>
                            {type === SupportType.BUG ?
                                "Describe your problem below and we will investigate it." :
                                "Describe your suggestion below and we will look into it."
                            }

                            <TextArea width="100%" value={textArea} onChange={e => setTextArea(e.target.value)} rows={6} />
                        </Label>

                        <Button
                            mt="6px"
                            fullWidth
                            type="submit"
                            disabled={loading}
                        >
                            <Icon name="heroPaperAirplane" size="1.5em" mr=".5em" color="primaryContrast" />
                            Send
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
