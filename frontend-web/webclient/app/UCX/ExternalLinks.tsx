import * as React from "react";
import {Box, Button, Flex, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";

export function openUcxUrl(url: string, appRelativePrefix = ""): void {
    const trimmed = url.trim();
    if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
        window.open(appRelativePrefix + trimmed, "_blank", "noopener,noreferrer");
        return;
    }

    let parsed: URL;
    try {
        parsed = new URL(trimmed);
    } catch {
        return;
    }
    if (parsed.protocol !== "https:") return;

    const target = parsed.href;
    void confirmExternalLink(target, parsed.origin).then(confirmed => {
        if (confirmed) window.open(target, "_blank", "noopener,noreferrer");
    });
}

function confirmExternalLink(url: string, origin: string): Promise<boolean> {
    return new Promise<boolean>(resolve => {
        let settled = false;

        const finish = (confirmed: boolean) => {
            if (settled) return;
            settled = true;
            dialogStore.success();
            resolve(confirmed);
        };

        dialogStore.addDialog(
            <ExternalLinkDialog url={url} origin={origin} onDecision={finish} />,
            () => {
                if (settled) return;
                settled = true;
                resolve(false);
            },
            true,
            slimModalStyle,
        );
    });
}

function ExternalLinkDialog(props: {
    url: string;
    origin: string;
    onDecision: (confirmed: boolean) => void;
}): React.ReactNode {
    return <Box p="20px">
        <Heading.h3>Opening external link</Heading.h3>
        <Text mt="8px" color="textSecondary">
            This link will open in a new tab.
        </Text>
        <Text bold mt="12px">Destination</Text>
        <Text mt="4px" style={{wordBreak: "break-all"}}>{props.url}</Text>
        <Text mt="8px" color="textSecondary">Origin: {props.origin}</Text>
        <Flex mt="20px" gap="8px" justifyContent="flex-end">
            <Button color="errorMain" onClick={() => props.onDecision(false)}>Cancel</Button>
            <Button color="primaryMain" onClick={() => props.onDecision(true)}>Open</Button>
        </Flex>
    </Box>;
}
