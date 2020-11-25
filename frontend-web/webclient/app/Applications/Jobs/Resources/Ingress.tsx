import * as React from "react";
import * as UCloud from "UCloud"
import {Checkbox, Flex, Input, Label} from "ui-components";
import {TextSpan} from "ui-components/Text";
import Warning from "ui-components/Warning";
import {dialogStore} from "Dialog/DialogStore";
import * as PublicLinks from "Applications/PublicLinks/Management";

export const IngressResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    inputRef: React.RefObject<HTMLInputElement>;
    enabled: boolean;
    setEnabled: React.Dispatch<React.SetStateAction<boolean>>;
}> = props => {
    const [url, setUrl] = React.useState<string>("");

    React.useEffect(() => {
        if (!props.inputRef) return;

        const current = props.inputRef.current;
        if (current === null) return;

        current.value = url;
    }, [props.inputRef, url]);

    return <Warning>TODO Ingress</Warning>;

    /*
    return props.application.invocation.applicationType !== "WEB" ? null : (
        <>
            <div>
                <Label mb={10}>
                    <Checkbox size={28} checked={props.enabled} onChange={() => {
                        props.setEnabled(!props.enabled);
                    }}/>
                    <TextSpan>Public link</TextSpan>
                </Label>
            </div>

            <div>
                {props.enabled ? (
                    <>
                        <Warning
                            warning="By enabling this setting, anyone with a link can gain access to the application."
                        />
                        <Label mt={20}>
                            <Flex alignItems={"center"}>
                                <TextSpan>https://app-</TextSpan>
                                <Input
                                    mx={"2px"}
                                    placeholder="Unique URL identifier"
                                    ref={props.inputRef}
                                    required
                                    onKeyDown={e => e.preventDefault()}
                                    onClick={event => {
                                        event.preventDefault();
                                        dialogStore.addDialog(
                                            <PublicLinks.PublicLinkManagement
                                                onSelect={pl => {
                                                    setUrl(pl.url);
                                                    dialogStore.success();
                                                }}
                                            />,
                                            () => 0
                                        );
                                    }}
                                />
                                <TextSpan>.cloud.sdu.dk</TextSpan>
                            </Flex>
                        </Label>
                    </>
                ) : (<></>)}
            </div>
        </>
    );
     */
};
