// Creator editor header
// =====================================================================================================================
// Renders the application title from the A2 draft. The title sits in the main-island header bar
// of the creator shell. It has no margins of its own — the shell controls spacing.

import * as React from "react";
import {Flex, Text} from "@/ui-components";
import {CreatorDraft} from "@/Applications/Creator/Draft";
import {AppLogoRaw, appColor, hashF} from "@/Applications/AppToolLogo";

export const EditorHeader: React.FunctionComponent<{
    draft: CreatorDraft;
}> = props => {
    const title = props.draft.application.title || props.draft.application.name || "Untitled application";
    return <Flex alignItems="center" gap="8px" minWidth={0}>
        <RawLogo title={title} />
        <Text fontSize={18} fontWeight={600}>{title}</Text>
    </Flex>;
};

function RawLogo(props: {title: string}): React.ReactNode {
    const hash = hashF(props.title);
    return <AppLogoRaw
        rot={[0, 15, 30][(hash >>> 10) % 3]}
        color1Offset={(hash >>> 30) & 3}
        color2Offset={(hash >>> 20) & 3}
        appC={appColor(hash)}
        size="24px"
    />;
}
