import * as React from "react";
import {Command, CommandIconProvider, CommandScope, useCommandProviderList} from "@/CommandPalette/index";
import {useCallback, useEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";
import Icon from "@/ui-components/Icon";
import Flex from "@/ui-components/Flex";
import Image from "@/ui-components/Image";
import Text from "@/ui-components/Text";
import {FileType} from "@/Files";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {NavigateFunction, useNavigate} from "react-router";
import AppRoutes from "@/Routes";
import {prettierString} from "@/UtilityFunctions";
import {fileName} from "@/Utilities/FileUtilities";
import {Box} from "@/ui-components";

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
    const navigate = useNavigate();

    /* TEST-DATA */
    const commandCount = useRef(0);
    const commands = React.useMemo(() => {
        const commands = someOtherProvidersWithOutput(query, navigate);
        commandCount.current = countCommands(commands);
        return commands;
    }, [query]);
    /* TEST-DATA */

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
            if (commandCount.current) {
                setCurrentIndex(idx => {
                    const newVal = Math.min((idx + 1), commandCount.current - 1);
                    scrollEntryIntoView(newVal);
                    return newVal;
                })
            }
        } else if (ev.code === "ArrowUp") {
            if (commandCount.current) {
                setCurrentIndex(idx => {
                    const newVal = Math.max((idx - 1), 0);
                    scrollEntryIntoView(newVal);
                    return newVal;
                });
            }
        } else if (ev.code === "Enter") {
            setCurrentIndex(idx => {
                if (idx === -1) return -1;
                const cmd = findActiveCommand(idx, commands);
                if (cmd) {
                    cmd.action();
                    setVisible(false);
                    setQuery("");
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

    const activeCommand = findActiveCommand(currentIndex, commands);

    return <div has-items={commandCount.current > 0 ? "" : undefined} className={wrapper}>
        <input
            autoFocus
            placeholder={"Search for anything on UCloud..."}
            onKeyDown={onInput}
            onChange={onChange}
            value={query}
            style={!commandCount.current ? undefined : {borderBottom: "1px solid var(--secondaryDark)", marginBottom: "8px"}} /* TODO(Jonas): Move this responsibility to the different categories */
        />
        <Box maxHeight="400px" overflowY="auto" data-command-pallete>
            <CommandScopeEntry activeCommand={activeCommand} title="" scope={commands[CommandScope.ThisPage]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Go to" scope={commands[CommandScope.GoTo]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Applications" scope={commands[CommandScope.Application]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Jobs" scope={commands[CommandScope.Job]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Drives" scope={commands[CommandScope.Drive]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Files" scope={commands[CommandScope.File]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Links" scope={commands[CommandScope.Link]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Project" scope={commands[CommandScope.Project]} />
            <CommandScopeEntry activeCommand={activeCommand} title="Accounting" scope={commands[CommandScope.Accounting]} />
        </Box>
    </div>;
};

function scrollEntryIntoView(index: number) {
    const pallette = document.querySelector("[data-command-pallete]");
    const entry = pallette?.children.item(index);
    if (entry) {entry.scrollIntoView({behavior: "smooth", block: "center"});}
}

function CommandScopeTitle({count, title}: {count: number; title: string}): React.ReactNode {
    if (!count) return null;
    return <Text mx="12px" mb="4px" bold style={{borderBottom: "1px solid var(--secondaryDark)"}}>{title}</Text>
}

function CommandScopeEntry({scope, title, activeCommand}: {scope: Command[]; title: string; activeCommand?: Command}): React.ReactNode {
    return <>
        {title ? <CommandScopeTitle title={title} count={scope.length} /> : null}
        {scope.map(c => <EntryWrapper key={c.title} command={c} active={c === activeCommand} />)}
    </>
}

function countCommands(commands: Record<CommandScope, Command[]>): number {
    return commands[CommandScope.ThisPage].length +
        commands[CommandScope.GoTo].length +
        commands[CommandScope.Application].length +
        commands[CommandScope.Job].length +
        commands[CommandScope.Drive].length +
        commands[CommandScope.File].length +
        commands[CommandScope.Link].length +
        commands[CommandScope.Project].length +
        commands[CommandScope.Accounting].length;
}

function findActiveCommand(i: number, commands: Record<CommandScope, Command[]>): Command | undefined {
    if (i === -1) return undefined;

    let index = i;
    for (const key of Object.keys(commands)) {
        if (index >= commands[key].length) index -= commands[key].length;
        else if (commands[key].length) return commands[key][index];
    }
    return undefined;
}

function EntryWrapper({command, active}: {command: Command; active: boolean}): React.ReactNode {
    return <Flex onClick={command.action} height="32px" cursor="pointer" backgroundColor={active ? `var(--primaryMain)` : undefined}>
        <div style={{marginTop: "auto", marginBottom: "auto", marginLeft: "16px"}}><CommandIcon key={command.icon.type} icon={command.icon} /></div>
        <Flex my="auto" mx="8px">
            <Text title={command.title}>{command.title}</Text>
            {command.description ? <Text ml="4px" color={active ? "var(--primaryLight)" : "var(--secondaryDark)"} title={command.description}>― {command.description}</Text> : null}
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

function someOtherProvidersWithOutput(query: string, navigate: NavigateFunction): Record<CommandScope, Command[]> {
    if (query) return {
        [CommandScope.ThisPage]: [
            mockFolder("DIRECTORY!", "Description!", "DIRECTORY", navigate),
            mockFolder("FILE!", "FILE DESCRIPTION", "FILE", navigate),
        ],
        [CommandScope.GoTo]: [mockGoto(SidebarTabId.FILES, AppRoutes.files.drives(), navigate)],
        [CommandScope.Application]: [
            mockApplication("Some app", "App description", navigate),
            mockApplication("Some other app", "App description", navigate)
        ],
        [CommandScope.Job]: [
            mockJob("0451", "RUNNING", navigate),
            mockJob("1405", "FAILED", navigate),
            mockJob("5014", "STOPPED", navigate),
        ],
        [CommandScope.Drive]: [],
        [CommandScope.File]: [
            mockFolder("DIRECTORY!", "Description!", "DIRECTORY", navigate),
            mockFolder("FILE!", "FILE DESCRIPTION", "FILE", navigate),
        ],
        [CommandScope.Link]: [{
            title: "???", description: "???", icon: {type: "simple", icon: "heroExclamationCircle"}, action() {

            }, scope: CommandScope.Link
        }],
        [CommandScope.Project]: [mockProject("Foobar", 22, navigate)],
        [CommandScope.Accounting]: [mockAccountingEntry("vg-gpu512", navigate)]
    };
    return {
        [CommandScope.ThisPage]: [],
        [CommandScope.GoTo]: [],
        [CommandScope.Application]: [],
        [CommandScope.Job]: [],
        [CommandScope.Drive]: [],
        [CommandScope.File]: [],
        [CommandScope.Link]: [],
        [CommandScope.Project]: [],
        [CommandScope.Accounting]: []
    };
}

function mockGoto(page: SidebarTabId, url: string, navigate: NavigateFunction): Command {
    return {
        title: prettierString(page),
        action() {
            navigate(url);
        },
        description: "Go to " + page.toLocaleLowerCase(),
        icon: {type: "simple", icon: "heroArrowUpRight"},
        scope: CommandScope.GoTo
    };
}

const APP_IMAGE_URLS = [
    "/api/hpc/apps/retrieveAppLogo?name=coder&darkMode=false&includeText=false&placeTextUnderLogo=false&cacheBust=0",
    "/api/hpc/apps/retrieveAppLogo?name=terminal-ubuntu&darkMode=false&includeText=false&placeTextUnderLogo=false&cacheBust=0",
    "/api/hpc/apps/retrieveAppLogo?name=cvat&darkMode=false&includeText=false&placeTextUnderLogo=false&cacheBust=0"
];
const IMAGE_COUNT = APP_IMAGE_URLS.length;
function mockFolder(path: string, description: string, type: FileType, navigate: NavigateFunction): Command {
    return {
        title: fileName(path),
        description,
        action() {
            navigate(AppRoutes.files.path(path));
        },
        icon: {type: "simple", icon: type === "DIRECTORY" ? "ftFolder" : "ftFileSystem"},
        scope: CommandScope.ThisPage
    }
}

function mockApplication(title: string, description: string, navigate: NavigateFunction): Command {
    return {
        title,
        description,
        icon: {type: "image", imageUrl: APP_IMAGE_URLS[(Math.random() * IMAGE_COUNT) | 0]},
        action() {
            navigate(AppRoutes.apps.group(title))
        },
        scope: CommandScope.Application
    }
}

function mockJob(title: string, description: string, navigate: NavigateFunction): Command {
    return mockApplication(title, description, navigate);
}

function mockProject(title: string, memberCount: number, navigate: NavigateFunction): Command {
    return {
        title,
        description: `${memberCount} members`,
        action() {
            navigate(AppRoutes.project.members())
        },
        icon: {type: "simple", icon: "projects"},
        scope: CommandScope.Project
    }
}

function mockAccountingEntry(title: string, navigate: NavigateFunction): Command {
    return {
        title,
        description: `${Math.random() * 1000 | 0} currencies remain`,
        action() {
            navigate(AppRoutes.accounting.usage())
        },
        icon: {type: "simple", icon: "heroCurrencyDollar"},
        scope: CommandScope.Accounting
    }
}

/* TODO:
    Scroll into view when element navigated to through keyboard is out of view-port
    onClick handler for 
*/