// Creator editor header
// =====================================================================================================================
// Renders the application title from the A2 draft. The title sits in the main-island header bar
// of the creator shell. It has no margins of its own — the shell controls spacing.

import * as React from "react";
import {Flex, Text} from "@/ui-components";
import {CreatorDraft} from "@/Applications/Creator/Draft";
import {SafeLogo} from "@/Applications/AppToolLogo";

export const EditorHeader: React.FunctionComponent<{
    draft: CreatorDraft;
}> = props => {
    const title = props.draft.application.title || props.draft.application.name || "Untitled application";
    return <Flex alignItems="center" gap="8px" minWidth={0}>
        <SafeLogo type="APPLICATION" name={props.draft.application.name} size="24px" />
        <Text fontSize={18} fontWeight={600}>{title}</Text>
    </Flex>;
};
