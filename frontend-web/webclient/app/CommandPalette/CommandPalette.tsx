import * as React from "react";
import {Command, CommandIconProvider, CommandScope, useCommandProviderList} from "@/CommandPalette/index";
import {useCallback, useEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";
import Icon from "@/ui-components/Icon";
import Flex from "@/ui-components/Flex";
import Image from "@/ui-components/Image";
import Text from "@/ui-components/Text";
import {Box, Truncate} from "@/ui-components";

const wrapper = injectStyle("command-palette", k => `
    ${k} {
        --own-width: 600px;
        --own-base-height: 48px;
        
        width: var(--own-width);
        min-height: var(--own-base-height);
        height: auto;
        
        
        position: fixed;
        top: calc(50vh - var(--own-base-height));
        left: calc(50vw - (var(--own-width) / 2));
        
        border-radius: 16px;
        color: var(--textPrimary);
        z-index: 99999999;
        
        box-shadow: var(--defaultShadow);
        background: var(--backgroundCardHover);

        &[has-items] {
            top: 25%
        }

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
        return result.sort((a, b) => a.scope - b.scope);
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
            if (commands.length) {
                setCurrentIndex(idx => {
                    const newVal = Math.min((idx + 1), commands.length - 1);
                    scrollEntryIntoView(newVal, "end");
                    return newVal;
                })
            }
        } else if (ev.code === "ArrowUp") {
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
                const cmd = commands[idx];
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
        setCurrentIndex(-1);
    }, [setQuery]);

    if (!visible) return null;

    const activeCommand = commands[currentIndex];

    return <div has-items={commands.length > 0 ? "" : undefined} className={wrapper}>
        <input
            autoFocus
            placeholder={"Search for anything on UCloud..."}
            onKeyDown={onInput}
            onChange={onChange}
            value={query}
        />
        <Box maxHeight="400px" overflowY="auto" data-command-pallete>
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="" scope={commands.filter(it => it.scope === CommandScope.ThisPage)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Go to" scope={commands.filter(it => it.scope === CommandScope.GoTo)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Applications" scope={commands.filter(it => it.scope === CommandScope.Application)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Jobs" scope={commands.filter(it => it.scope === CommandScope.Job)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Drives" scope={commands.filter(it => it.scope === CommandScope.Drive)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Files" scope={commands.filter(it => it.scope === CommandScope.File)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Links" scope={commands.filter(it => it.scope === CommandScope.Link)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Project" scope={commands.filter(it => it.scope === CommandScope.Project)} />
            <CommandScopeEntry onClick={onActivate} activeCommand={activeCommand} title="Accounting" scope={commands.filter(it => it.scope === CommandScope.Accounting)} />
        </Box>
    </div>;
};

function scrollEntryIntoView(index: number, scroll: ScrollLogicalPosition) {
    const pallette = document.querySelector("[data-command-pallete]");
    const entry = pallette?.children.item(index);
    if (entry) {entry.scrollIntoView({behavior: "smooth", block: scroll});}
}

function CommandScopeTitle({count, title}: {count: number; title: string}): React.ReactNode {
    if (!count) return null;
    return <Text mx="12px" mb="4px" bold style={{borderBottom: "1px solid var(--secondaryDark)"}}>{title}</Text>
}

function CommandScopeEntry({onClick, scope, title, activeCommand}: {onClick(): void; scope: Command[]; title: string; activeCommand?: Command}): React.ReactNode {
    return <>
        {title ? <CommandScopeTitle title={title} count={scope.length} /> : null}
        {scope.map(c => <EntryWrapper onClick={onClick} key={c.title} command={c} active={c === activeCommand} />)}
    </>
}

function EntryWrapper({command, active, onClick}: {command: Command; active: boolean; onClick(): void;}): React.ReactNode {
    return <Flex onClick={() => {
        onClick();
        command.action();
    }} height="32px" cursor="pointer" backgroundColor={active ? `var(--primaryMain)` : undefined}>
        <div style={{marginTop: "auto", marginBottom: "auto", marginLeft: "16px"}}><CommandIcon key={command.icon.type} icon={command.icon} /></div>
        <Flex my="auto" mx="8px" width="100%">
            <Truncate maxWidth={"250px"} title={command.title}>{command.title}</Truncate>
            {command.description ? <Truncate maxWidth={"200px"} ml="4px" color={active ? "var(--primaryLight)" : "var(--secondaryDark)"} title={command.description}>― {command.description}</Truncate> : null}
            <Box ml="auto" />
            <Text>Go to</Text>
        </Flex>
    </Flex>
}

const IMAGE_SIZE = 18;
function CommandIcon({icon}: {icon: CommandIconProvider}) {
    switch (icon.type) {
        case "dom": {
            const ref = useRef<HTMLDivElement>(null);
            React.useEffect(() => {
                if (ref.current)
                    ref.current.append(icon.dom(IMAGE_SIZE));
            }, [ref.current]);
            return <div ref={ref} />;
        }
        case "image": {
            return <Image src={icon.imageUrl} height={`${IMAGE_SIZE}px`} width={`${IMAGE_SIZE}px`} />;
        }
        case "simple": {
            return <Icon name={icon.icon} size={IMAGE_SIZE} />
        }
    }
}

/* TODO:
    Scroll into view when element navigated to through keyboard is out of view-port
    Close on outside click
    Actually hook up to sidebar
    Test "dom" image
    Truncate description and title
    CSS for last element in list.
*/