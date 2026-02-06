import * as React from "react";
import {Box, Button, Checkbox, Flex, Label} from "@/ui-components";
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
    title: string;
    checked: boolean;
    onClick: () => void;
    onChange?: () => void;
    disabled?: boolean;
    size?: number;
    description?: React.ReactNode;
}

export function SettingsCheckboxRow({
    title,
    checked,
    onClick,
    onChange = () => undefined,
    disabled = false,
    size = 27,
    description
}: SettingsCheckboxRowProps): React.ReactNode {
    return (
        <Label ml={10} mt={8} width="100%" style={{display: "inline-block"}}>
            <Flex alignItems="center">
                <Checkbox
                    size={size}
                    onClick={onClick}
                    onChange={onChange}
                    checked={checked}
                    disabled={disabled}
                />
                <Box ml="8px" mt="2px">
                    <div>{title}</div>
                    {description ? <div style={{fontSize: "0.9em", color: "var(--textSecondary)"}}>{description}</div> : null}
                </Box>
            </Flex>
        </Label>
    );
}
