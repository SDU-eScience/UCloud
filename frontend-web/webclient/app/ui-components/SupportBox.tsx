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
import Icon from "./Icon";
import Label from "./Label";
import Radio from "./Radio";
import Text from "./Text";
import {Spacer} from "./Spacer";
import {TextDiv, TextSpan} from "./Text";
import TextArea from "./TextArea";

const enum SupportType {
    SUGGESTION = "SUGGESTION",
    BUG = "BUG"
}

export default function Support(): JSX.Element {
    const textArea = useRef<HTMLTextAreaElement>(null);
    const titleArea = useRef<HTMLTextAreaElement>(null);
    const supportBox = useRef<HTMLTextAreaElement>(null);
    const [loading, setLoading] = useState(false);
    const [visible, setVisible] = useState(false);
    const [type, setType] = useState(SupportType.SUGGESTION);

    function handleESC(e: KeyboardEvent): void {
        if (e.keyCode === KeyCode.ESC) setVisible(false);
    }

    function handleClickOutside(event): void {
        if (supportBox.current && !supportBox.current.contains(event.target) && visible)
            setVisible(false);
    }

    async function onSubmit(event: React.FormEvent): Promise<void> {
        event.preventDefault();
        const text = textArea.current?.value ?? "";
        const title = titleArea.current?.value ?? "";
        if (text.trim()) {
            try {
                setLoading(true);
                await Client.post("/support/ticket", {subject: title, message: `${type}: ${text}`});
                textArea.current!.value = "";
                titleArea.current!.value = "";
                setVisible(false);
                setLoading(false);
                snackbarStore.addSuccess("Support ticket submitted!", false);
            } catch (e) {
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred submitting the message"), false);
            }
        } else {
            snackbarStore.addFailure("Support message can't be empty.", false);
        }
    }

    useEffect(() => {
        document.addEventListener("keydown", handleESC);
        document.addEventListener("mousedown", handleClickOutside);
        return () => {
            document.removeEventListener("keydown", handleESC);
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    return (
        <ClickableDropdown
            colorOnHover={false}
            keepOpenOnClick
            trigger={(
                <Flex width="48px" justifyContent="center">
                    <Icon name={"chat"} size="24px" color="headerIconColor" color2="headerBg" />
                </Flex>
            )}
            width="650px"
            right="10px"
            top="37px"
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
