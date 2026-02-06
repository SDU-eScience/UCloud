import * as React from "react";
import {Box, Button, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";

interface SettingsSectionProps {
    title: string;
    children: React.ReactNode;
    mb?: number;
    showTitle?: boolean;
}

export function SettingsSection({title, children, mb = 16, showTitle = true}: SettingsSectionProps): React.ReactNode {
    return (
        <Box mb={mb}>
            {showTitle ? <Heading.h2>{title}</Heading.h2> : null}
            <Box mt="0.5em">{children}</Box>
        </Box>
    );
}

interface SettingsActionsProps {
    submitLabel: string;
    disabled?: boolean;
}

export function SettingsActions({submitLabel, disabled}: SettingsActionsProps): React.ReactNode {
    return (
        <Button
            mt="1em"
            type="submit"
            color="successMain"
            disabled={disabled}
        >
            {submitLabel}
        </Button>
    );
}

interface SettingsCheckboxRowProps {
    children: React.ReactNode;
}

export function SettingsCheckboxRow({children}: SettingsCheckboxRowProps): React.ReactNode {
    return (
        <Label ml={10} width="45%" style={{display: "inline-block"}}>
            {children}
        </Label>
    );
}
