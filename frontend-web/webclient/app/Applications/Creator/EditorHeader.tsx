// Creator editor header
// =====================================================================================================================
// Renders the application title from the A2 draft. The title sits in the main-island header bar
// of the creator shell. It has no margins of its own — the shell controls spacing.

import * as React from "react";
import {Text} from "@/ui-components";
import {CreatorDraft} from "@/Applications/Creator/Draft";

export const EditorHeader: React.FunctionComponent<{
    draft: CreatorDraft;
}> = props => {
    const title = props.draft.application.title || props.draft.application.name || "Untitled application";
    return <Text fontSize={24} fontWeight={600}>{title}</Text>;
};
