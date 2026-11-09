import * as React from "react";
import {Box, Button, Divider, Flex, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {dialogStore} from "@/Dialog/DialogStore";

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
        );
    });
}

function ExternalLinkDialog(props: {
    url: string;
    origin: string;
    onDecision: (confirmed: boolean) => void;
}): React.ReactNode {
    return <div>
        <Heading.h3>Opening external link</Heading.h3>
        <Divider />
        <Flex flexDirection="column" gap="16px" mt="16px" minHeight="200px">
            <Text color="textSecondary">
                This link will open in a new tab.
            </Text>
            <Box>
                <Text bold mb="4px">Destination</Text>
                <Text style={{wordBreak: "break-all"}}>{props.url}</Text>
            </Box>
            <Text color="textSecondary">Origin: {props.origin}</Text>
        </Flex>
        <Flex
            mt="16px"
            mx="-20px"
            mb="-20px"
            px="20px"
            py="12px"
            gap="8px"
            justifyContent="flex-end"
            background="var(--dialogToolbar)"
        >
            <Button color="errorMain" onClick={() => props.onDecision(false)}>Cancel</Button>
            <Button color="primaryMain" onClick={() => props.onDecision(true)}>Open</Button>
        </Flex>
    </div>;
}
