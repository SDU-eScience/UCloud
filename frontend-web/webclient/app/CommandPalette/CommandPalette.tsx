import * as React from "react";
import {Command, CommandIconProvider, CommandScope, useCommandProviderList} from "@/CommandPalette/index";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";
import Icon from "@/ui-components/Icon";
import Flex from "@/ui-components/Flex";
import Image from "@/ui-components/Image";
import Text from "@/ui-components/Text";
import {Box, Truncate} from "@/ui-components";
import {groupBy} from "@/Utilities/CollectionUtilities";

const wrapper = injectStyle("command-palette", k => `
    ${k} {
        --own-width: 600px;
        --own-base-height: 48px;
        
        width: var(--own-width);
        min-height: var(--own-base-height);
        height: auto;
        
        
        position: fixed;
        top: 25%;
        left: calc(50vw - (var(--own-width) / 2));
        
        border-radius: 16px;
        color: var(--textPrimary);
        z-index: 99999999;
        
        box-shadow: var(--defaultShadow);
        background: var(--backgroundCardHover);

        & input {
            width: calc(100% - 2 * 16px);
            height: var(--own-base-height);
            outline: none;
            border: 0;
            background: transparent;
            font-size: calc(0.4 * var(--own-base-height));
            margin: 0 16px;
        }
    }
`);

export function isCommandPaletteTriggerEvent(ev: KeyboardEvent): boolean {
    return ((ev.metaKey || ev.ctrlKey) && ev.code === "KeyP");
}

export const CommandPalette: React.FunctionComponent = () => {
    if (!hasFeature(Feature.COMMAND_PALETTE)) return false;

    const commandProviders = useCommandProviderList();
    const [visible, setVisible] = useState(false);
    const queryRef = useRef("");
    const [query, setQuery] = useState("");
    const [currentIndex, setCurrentIndex] = React.useState(-1);

    const commands = React.useMemo(() => {
        const result: Command[] = [];
        for (const p of commandProviders) {
            p(query, c => result.push(c));
        }
        return result;
    }, [commandProviders, query]);

    useEffect(() => {
        queryRef.current = query;
    }, [query]);

    useEffect(() => {
        const listener = (ev: WindowEventMap["keydown"]) => {
            if (isCommandPaletteTriggerEvent(ev)) {
                ev.preventDefault();
                ev.stopPropagation();
                setVisible(prev => !prev);
            }
        };

        window.addEventListener("keydown", listener)

        return () => {
            window.removeEventListener("keydown", listener);
        };
    }, []);

    const open = useCallback(() => {
        setVisible(true);
    }, []);

    const close = useCallback(() => {
        setVisible(false);
    }, []);

    const onActivate = useCallback(() => {
        setQuery("");
        setCurrentIndex(-1);
        setVisible(false);
    }, []);

    const onInput = useCallback((ev: React.KeyboardEvent) => {
        ev.stopPropagation();
        if (ev.code === "Escape") {
            if (queryRef.current != "") {
                setQuery("");
                setCurrentIndex(-1);
            } else {
                close();
            }
        } else if (ev.code === "ArrowDown") {
            ev.preventDefault();
            if (commands.length) {
                setCurrentIndex(idx => {
                    const newVal = Math.min((idx + 1), commands.length - 1);
                    scrollEntryIntoView(newVal, "start");
                    return newVal;
                })
            }
        } else if (ev.code === "ArrowUp") {
            ev.preventDefault();
            if (commands.length) {
                setCurrentIndex(idx => {
                    const newVal = Math.max((idx - 1), 0);
                    scrollEntryIntoView(newVal, "start");
                    return newVal;
                });
            }
        } else if (ev.code === "Enter") {
            setCurrentIndex(idx => {
                if (idx === -1) return -1;
                const cmd = findActiveCommand(idx, groupBy(commands, it => it.scope));
                if (cmd) {
                    onActivate();
                    cmd.action();
                }
                return -1;
            })
        }
    }, [setQuery, commands]);

    const onChange = useCallback((ev: React.SyntheticEvent) => {
        setQuery((ev.target as HTMLInputElement).value);
        setCurrentIndex(0);
    }, [setQuery]);

    const groupedCommands = useMemo(() => {
        return groupBy(commands, it => it.scope);
    }, [commands]);

    const divRef = React.useRef<HTMLDivElement>(null);
    const closeOnOutsideClick = React.useCallback(e => {
        if (divRef.current && !divRef.current.contains(e.target)) {
            onActivate();
        }
    }, []);

    React.useEffect(() => {
        document.addEventListener("mousedown", closeOnOutsideClick);
        return () => document.removeEventListener("mousedown", closeOnOutsideClick);
    }, []);

    const activeCommand: Command | undefined = React.useMemo(() => {
        return findActiveCommand(currentIndex, groupedCommands)
    }, [currentIndex, groupedCommands]);

    if (!visible) return null;

    return <div ref={divRef} className={wrapper}>
        <input
            autoFocus
            placeholder={"Search for actions on UCloud..."}
            onKeyDown={onInput}
            onChange={onChange}
            value={query}
        />
        <Box maxHeight="400px" px="8px" pb="8px" overflowY="auto" data-command-palette>
            {Object.values(CommandScope).map((scope: CommandScope) =>
                <CommandScopeEntry
                    key={scope}
                    onClick={onActivate}
                    scope={groupedCommands[scope] ?? []}
                    title={scope}
                    activeCommand={activeCommand}
                />
            )}
        </Box>
    </div>;
};

function findActiveCommand(currentIndex: number, groupedCommands: Record<string, Command[]>): Command | undefined {
    if (currentIndex === -1) return undefined;
    let idx = currentIndex;

    for (const scope of Object.values(CommandScope)) {
        if (!groupedCommands[scope]?.length) continue;

        if (idx >= groupedCommands[scope].length) {
            idx -= groupedCommands[scope].length;
        } else {
            return groupedCommands[scope]?.[idx];
        }
    };

    return undefined;
}

function scrollEntryIntoView(index: number, scroll: ScrollLogicalPosition) {
    const entry = document.querySelector("[data-command-palette]")?.querySelectorAll("[data-entry]").item(index);;
    if (entry) {
        entry.scrollIntoView({block: scroll});
    }
}

function CommandScopeTitle({count, title}: {count: number; title: string}): React.ReactNode {
    if (!count) return null;
    return <Text mx="12px" my="8px" bold style={{borderBottom: "1px solid var(--secondaryDark)"}}>
        {title}
    </Text>
}

function CommandScopeEntry({onClick, scope, title, activeCommand}: {
    onClick(): void;
    scope: Command[];
    title: string;
    activeCommand?: Command;
}): React.ReactNode {
    return <>
        {title ? <CommandScopeTitle title={title} count={scope.length} /> : null}
        {scope.map((c, idx) => <EntryWrapper onClick={onClick} key={c.title + idx} command={c} active={c === activeCommand} />)}
    </>
}

function EntryWrapper({command, active, onClick}: {
    command: Command;
    active: boolean;
    onClick(): void;
}): React.ReactNode {
    return <Flex
        onClick={() => {
            onClick();
            command.action();
        }}
        height="32px"
        cursor="pointer"
        borderRadius={"6px"}
        backgroundColor={active ? `var(--primaryMain)` : undefined}
        color={active ? "primaryContrast" : undefined}
        data-entry
    >
        <Box my="auto" ml="16px">
            <CommandIcon key={command.icon.type} label={command.title + " icon"} icon={command.icon} active={active} />
        </Box>

        <Flex my="auto" mx="8px" width="100%">
            <Truncate maxWidth={"250px"} title={command.title}>
                {command.title}
            </Truncate>

            {command.description ?
                <Truncate
                    maxWidth={"200px"}
                    ml="4px"
                    color={active ? "var(--primaryLight)" : "var(--secondaryDark)"}
                    title={command.description}
                >
                    ― {command.description}
                </Truncate>
                : null
            }

            <Box ml="auto" />
            <Text>{command.actionText ?? ""}</Text>
        </Flex>
    </Flex>
}

const IMAGE_SIZE = 18;
function CommandIcon({icon, active, label}: {icon: CommandIconProvider; active: boolean; label: string}) {
    switch (icon.type) {
        case "image": {
            return <Image alt={label} src={icon.imageUrl} height={`${IMAGE_SIZE}px`} objectFit="contain" width={`${IMAGE_SIZE}px`} />;
        }
        case "simple": {
            return <Icon name={icon.icon} size={IMAGE_SIZE} color={icon.color ?? (active ? "primaryContrast" : "iconColor")} color2={icon.color2 ?? (active ? "primaryContrastAlt" : "iconColor2")} />
        }
    }
}
