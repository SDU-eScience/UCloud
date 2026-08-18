import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {Box, Button, Checkbox, Flex, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {MainContainer} from "@/ui-components/MainContainer";

export interface SettingsNavSection {
    id: string;
    label: string;
}

interface SettingsPageProps {
    title: string;
    titleActions?: React.ReactNode;
    sections: SettingsNavSection[];
    children: React.ReactNode;
}

export function SettingsPage({title, titleActions, sections, children}: SettingsPageProps): React.ReactNode {
    return <MainContainer
        headerAtTop
        header={<div className={SettingsHeaderClass}>
            <h3 className="title">{title}</h3>
            {titleActions}
        </div>}
        main={<div className={SettingsPageContainerClass}>
            <div className={SettingsPageClass}>
                <SettingsNavigator sections={sections} />
                <main className="settings-page-content">{children}</main>
            </div>
        </div>}
    />;
}

interface SettingsSectionProps {
    id?: string;
    title: string;
    description?: React.ReactNode;
    children: React.ReactNode;
    mb?: number;
    showTitle?: boolean;
}

export function SettingsSection({
    id,
    title,
    description,
    children,
    mb = 40,
    showTitle = true
}: SettingsSectionProps): React.ReactNode {
    return <section
        id={id}
        className={`${SettingsSectionClass}${showTitle ? " settings-section-titled" : ""}`}
        style={{marginBottom: mb}}
    >
        {showTitle ? <Heading.h2 className="settings-section-title" fontSize="20px">{title}</Heading.h2> : null}
        <div className="settings-section-content">
            {description ? <div className="settings-section-description">{description}</div> : null}
            {children}
        </div>
    </section>;
}

interface SettingsNavigatorProps {
    sections: SettingsNavSection[];
}

export function SettingsNavigator({sections}: SettingsNavigatorProps): React.ReactNode {
    const [activeSection, setActiveSection] = React.useState(() => {
        const hash = decodeURIComponent(window.location.hash.slice(1));
        return sections.some(section => section.id === hash) ? hash : sections[0]?.id ?? "";
    });
    const sectionKey = sections.map(section => section.id).join("|");

    React.useEffect(() => {
        const scrollRoot = document.querySelector<HTMLElement>('[data-component="router-wrapper"]');
        if (!scrollRoot || sections.length === 0) return;

        let frame = 0;
        const updateActiveSection = () => {
            cancelAnimationFrame(frame);
            frame = requestAnimationFrame(() => {
                const rootTop = scrollRoot.getBoundingClientRect().top;
                const activationLine = rootTop + 112;
                const elements = sections
                    .map(section => document.getElementById(section.id))
                    .filter((element): element is HTMLElement => element !== null);

                if (elements.length === 0) return;

                let active = elements[0].id;
                for (const element of elements) {
                    if (element.getBoundingClientRect().top <= activationLine) active = element.id;
                }

                const atBottom = scrollRoot.scrollTop + scrollRoot.clientHeight >= scrollRoot.scrollHeight - 2;
                if (atBottom) active = elements[elements.length - 1].id;

                setActiveSection(previous => previous === active ? previous : active);
            });
        };

        const scrollToHash = () => {
            const hash = decodeURIComponent(window.location.hash.slice(1));
            if (!sections.some(section => section.id === hash)) return;
            document.getElementById(hash)?.scrollIntoView({block: "start"});
        };

        scrollToHash();
        updateActiveSection();
        scrollRoot.addEventListener("scroll", updateActiveSection, {passive: true});
        window.addEventListener("resize", updateActiveSection);
        window.addEventListener("hashchange", scrollToHash);

        return () => {
            cancelAnimationFrame(frame);
            scrollRoot.removeEventListener("scroll", updateActiveSection);
            window.removeEventListener("resize", updateActiveSection);
            window.removeEventListener("hashchange", scrollToHash);
        };
    }, [sectionKey]);

    React.useEffect(() => {
        if (!activeSection || window.location.hash === `#${activeSection}`) return;
        history.replaceState(null, "", `#${activeSection}`);
    }, [activeSection]);

    return <nav className={SettingsNavigatorClass} aria-label="On this page">
        <Heading.h5 className="settings-nav-title">On this page</Heading.h5>
        <div className="settings-nav-links">
            {sections.map(section => <a
                key={section.id}
                href={`#${section.id}`}
                aria-current={activeSection === section.id ? "location" : undefined}
                onClick={event => {
                    event.preventDefault();
                    const element = document.getElementById(section.id);
                    if (!element) return;
                    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
                    element.scrollIntoView({behavior: reduceMotion ? "auto" : "smooth", block: "start"});
                    history.replaceState(null, "", `#${section.id}`);
                    setActiveSection(section.id);
                }}
            >
                {section.label}
            </a>)}
        </div>
    </nav>;
}

interface SettingsActionProps {
    title: React.ReactNode;
    description?: React.ReactNode;
    action: React.ReactNode;
}

export function SettingsAction({title, description, action}: SettingsActionProps): React.ReactNode {
    return <div className={`${SettingsActionClass} settings-action`}>
        <div className="settings-action-copy">
            <div className="settings-action-title">{title}</div>
            {description ? <div className="settings-action-description">{description}</div> : null}
        </div>
        <div className="settings-action-control">{action}</div>
    </div>;
}

interface SettingsActionsProps {
    submitLabel: string;
    disabled?: boolean;
}

export function SettingsActions({submitLabel, disabled}: SettingsActionsProps): React.ReactNode {
    return <Button mt="1em" type="submit" color="successMain" disabled={disabled}>
        {submitLabel}
    </Button>;
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
    return <Label ml={10} mt={8} width="100%" style={{display: "inline-block"}}>
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
    </Label>;
}

const SettingsHeaderClass = injectStyle("settings-header", k => `
    ${k} {
        align-items: center;
        display: flex;
        gap: 16px;
        height: 100%;
        justify-content: space-between;
        margin: 0 auto;
        max-width: 1200px;
        width: 100%;
    }
`);

const SettingsPageContainerClass = injectStyle("settings-page-container", k => `
    ${k} {
        container-type: inline-size;
    }
`);

const SettingsPageClass = injectStyle("settings-page", k => `
    ${k} {
        display: grid;
        gap: 40px;
        margin: 0 auto;
        max-width: 1200px;
    }

    ${k} > .settings-page-content {
        min-width: 0;
    }

    @container (min-width: 720px) {
        ${k} {
            grid-template-columns: 200px minmax(0, 1fr);
        }
    }
`);

const SettingsNavigatorClass = injectStyle("settings-navigator", k => `
    ${k} {
        align-self: start;
        min-width: 0;
    }

    ${k} .settings-nav-links {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 12px;
    }

    ${k} a {
        border: 1px solid var(--borderColor);
        border-radius: 999px;
        color: var(--textPrimary);
        font-size: 0.9rem;
        padding: 6px 10px;
        text-decoration: none;
    }

    ${k} a:hover,
    ${k} a[aria-current="location"] {
        border-color: var(--primaryMain);
        color: var(--primaryMain);
    }

    @container (min-width: 720px) {
        ${k} {
            position: sticky;
            top: 80px;
        }

        ${k} .settings-nav-title {
            display: none;
        }

        ${k} .settings-nav-links {
            border-left: 1px solid var(--borderColor);
            display: flex;
            flex-direction: column;
            gap: 2px;
            margin-top: 0;
            padding: 4px 0;
        }

        ${k} a {
            border: 0;
            border-left: 3px solid transparent;
            border-radius: 0;
            margin-left: -2px;
            padding: 8px 12px;
        }

        ${k} a:hover {
            background: var(--rowHover);
        }

        ${k} a[aria-current="location"] {
            background: var(--rowActive);
            border-left-color: var(--primaryMain);
            color: var(--textPrimary);
            font-weight: 600;
        }
    }
`);

const SettingsSectionClass = injectStyle("settings-section", k => `
    ${k} {
        scroll-margin-top: 80px;
    }

    ${k} .settings-section-description {
        color: var(--textSecondary);
        margin-bottom: 16px;
        max-width: 70ch;
    }

    ${k}.settings-section-titled > .settings-section-content {
        margin-top: 16px;
    }

    ${k} > .settings-section-title {
        border-bottom: 1px solid var(--document-border, var(--borderColor));
        padding-bottom: 5px;
    }

    ${k} > .settings-section-content > .settings-action:first-child {
        border-top: 0;
        padding-top: 0;
    }
`);

const SettingsActionClass = injectStyle("settings-action", k => `
    ${k} {
        align-items: flex-start;
        border-top: 1px solid var(--borderColor);
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
        justify-content: space-between;
        padding: 20px 0;
    }

    ${k} .settings-action-copy {
        flex: 1 1 360px;
    }

    ${k} .settings-action-title {
        font-weight: 600;
    }

    ${k} .settings-action-description {
        color: var(--textSecondary);
        margin-top: 4px;
        max-width: 60ch;
    }

    ${k} .settings-action-control {
        flex: 0 0 auto;
    }
`);
