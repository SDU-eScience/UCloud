import * as React from "react";
import {Box, Button, Checkbox, Flex, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";

interface SettingsSectionProps {
    id?: string;
    title: string;
    children: React.ReactNode;
    mb?: number;
    showTitle?: boolean;
}

export function SettingsSection({id, title, children, mb = 16, showTitle = true}: SettingsSectionProps): React.ReactNode {
    return (
        <div id={id} style={{scrollMarginTop: "96px"}}>
            <Box mb={mb}>
                {showTitle ? <Heading.h2>{title}</Heading.h2> : null}
                <Box mt="0.5em">{children}</Box>
            </Box>
        </div>
    );
}

export interface SettingsNavSection {
    id: string;
    label: string;
}

interface SettingsNavigatorProps {
    sections: SettingsNavSection[];
}

export function SettingsNavigator({sections}: SettingsNavigatorProps): React.ReactNode {
    React.useEffect(() => {
        const hash = window.location.hash.replace("#", "");
        if (!hash) return;
        if (!sections.find(section => section.id === hash)) return;
        const element = document.getElementById(hash);
        element?.scrollIntoView({behavior: "smooth", block: "start"});
    }, [sections]);

    return (
        <Box>
            <Heading.h5>On this page</Heading.h5>
            <Flex mt={12} flexWrap="wrap" justifyContent={"space-between"}>
                {sections.map(section => (
                    <a
                        key={section.id}
                        href={`#${section.id}`}
                        onClick={event => {
                            event.preventDefault();
                            const element = document.getElementById(section.id);
                            if (!element) return;
                            element.scrollIntoView({behavior: "smooth", block: "start"});
                            history.replaceState(null, "", `#${section.id}`);
                        }}
                        style={{
                            border: "1px solid var(--borderColor)",
                            borderRadius: "999px",
                            padding: "6px 10px",
                            color: "var(--textPrimary)",
                            textDecoration: "none",
                            fontSize: "0.9rem"
                        }}
                    >
                        {section.label}
                    </a>
                ))}
            </Flex>
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
