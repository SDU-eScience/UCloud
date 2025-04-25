import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {default as ReactModal} from "react-modal";
import {Box, Button, ExternalLink, Flex, Icon, Select} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {CardClass} from "@/ui-components/Card";
import {ApplicationParameterNS, NameAndVersion} from "@/Applications/AppStoreApi";
import {AppLogo, hashF} from "@/Applications/AppToolLogo";
import {TooltipV2} from "@/ui-components/Tooltip";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import * as Heading from "@/ui-components/Heading";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {injectStyle} from "@/Unstyled";
import {SelectProps} from "@/ui-components/Select";
import {deferLike, useEffectSkipMount} from "@/UtilityFunctions";
import {Toggle} from "@/ui-components/Toggle";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;

interface ModuleListProps extends WidgetProps {
    parameter: ApplicationParameterNS.ModuleList;
}

interface ModulesWithNotes {
    loadList: ApplicationParameterNS.Module[]
    errors: Record<string, true>;
}

function addOrUpdateModules(
    allSoftware: Record<string, ApplicationParameterNS.Module[]>,
    current: ApplicationParameterNS.Module[],
    addition: ApplicationParameterNS.Module,
    allowVersionChangeOfAddition: boolean,
    addDependencyChain: boolean,
): ModulesWithNotes {
    const currentErrors = evaluateConfiguration(current);
    const currentErrorCount = Object.keys(currentErrors).length;

    const versioned: Record<string, string> = {};
    for (const module of current) {
        const [name, version] = moduleNameToNameAndVersion(module.name);
        versioned[name] = version;
    }

    if (addDependencyChain) {
        let candidates = [addition];
        if (allowVersionChangeOfAddition) {
            const [name] = moduleNameToNameAndVersion(addition.name);
            candidates = allSoftware[name] ?? [];
            candidates = [addition, ...candidates.filter(it => it !== addition)];
        }

        for (const candidate of candidates) {
            const baseSoftwareToLoad = {...versioned};
            const [name, version] = moduleNameToNameAndVersion(candidate.name);
            baseSoftwareToLoad[name] = version;

            if (candidate.dependsOn.length === 0) {
                const proposedList = reorderSoftware(allSoftware, baseSoftwareToLoad);
                if (Object.keys(proposedList.errors).length <= currentErrorCount) {
                    return proposedList;
                }
            } else {
                for (const dependencyChain of candidate.dependsOn) {
                    const softwareToLoadWithChain = {...baseSoftwareToLoad};
                    for (const dependency of dependencyChain) {
                        const [depName, depVersion] = moduleNameToNameAndVersion(dependency);
                        softwareToLoadWithChain[depName] = depVersion;
                    }

                    const proposedList = reorderSoftware(allSoftware, softwareToLoadWithChain);
                    if (Object.keys(proposedList.errors).length <= currentErrorCount) {
                        return proposedList;
                    }
                }
            }
        }
    }

    const baseSoftwareToLoad = {...versioned};
    const [name, version] = moduleNameToNameAndVersion(addition.name);
    baseSoftwareToLoad[name] = version;
    return reorderSoftware(allSoftware, baseSoftwareToLoad);
}

function reorderSoftware(
    allSoftware: Record<string, ApplicationParameterNS.Module[]>,
    toLoad: Record<string, string>
): ModulesWithNotes {
    let errors: Record<string, true> = {};
    const result: ApplicationParameterNS.Module[] = [];
    const remaining = {...toLoad};

    let keys: string[] = Object.keys(remaining);
    while (keys.length > 0) {
        keys.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
        console.log(keys);

        for (const name of keys) {
            const version = remaining[name];
            const module = (allSoftware[name] ?? []).find(it => moduleNameToNameAndVersion(it.name)[1] === version);
            if (!module) continue;

            let dependencyChainOk = false;
            if (module.dependsOn.length === 0) {
                dependencyChainOk = true;
            } else {
                dependencyChainOk = module.dependsOn.some(chain => {
                    return chain.every(dep => {
                        return result.some(loaded => {
                            return dep === loaded.name;
                        });
                    });
                })
            }

            if (dependencyChainOk) {
                result.push(module);
                delete remaining[name];
            }
        }

        let prevLength = keys.length;
        keys = Object.keys(remaining);
        if (prevLength === keys.length) {
            // Couldn't satisfy all requirements. Add remaining at the end in random order.
            for (const k of keys) {
                const version = remaining[k];
                const module = (allSoftware[k] ?? []).find(it => moduleNameToNameAndVersion(it.name)[1] === version);
                if (!module) continue;
                result.push(module);

                errors[k] = true;
            }

            break;
        }
    }

    return {loadList: result, errors};
}

const highlightChange = injectStyle("highlight-change", k => `
    @keyframes highlightFade {
        0% {
            filter: invert(0.6);
        }
        100% {
            filter: invert(0);
        }
    }

    ${k} {
        animation: highlightFade 0.4s ease-out;
    };
`);

const HighlightedSelected: React.FunctionComponent<SelectProps & {children: React.ReactNode;}> = ({children, ...props}) => {
    const ref = useRef<HTMLSelectElement>(null);
    useEffectSkipMount(() => {
        const s = ref.current;
        if (s) {
            s.classList.remove(highlightChange);
            deferLike(() => s.classList.add(highlightChange));
        }
    }, [props.value]);
    return <Select selectRef={ref} {...props}>{children}</Select>;
}

const HighlightedContainer: React.FunctionComponent<{children: React.ReactNode;}> = ({children}) => {
    const ref = useRef<HTMLDivElement>(null);
    useEffectSkipMount(() => {
        const s = ref.current;
        if (s) {
            s.classList.remove(highlightChange);
            setTimeout(() => s.classList.add(highlightChange), 0);
        }
    }, [children]);
    return <div style={{color: "var(--textPrimary)", background: "var(--cardBackground)"}} ref={ref}>{children}</div>;
}

function evaluateConfiguration(loadList: ApplicationParameterNS.Module[]): Record<string, true> {
    let errors: Record<string, true> = {};
    for (const module of loadList) {
        const [name] = moduleNameToNameAndVersion(module.name);

        const ok = module.dependsOn.length === 0 || module.dependsOn.some(chain => {
            return chain.every(dep => {
                return loadList.some(loaded => {
                    return dep === loaded.name;
                });
            });
        });

        if (!ok) {
            errors[name] = true;
        }
    }

    return errors;
}

export const ModuleListParameter: React.FunctionComponent<ModuleListProps> = props => {
    const error = props.errors[props.parameter.name];

    const valueInput = () => document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;

    const [selectedModules, setSelectedModules] = useState<ApplicationParameterNS.Module[]>([]);
    const [showDocs, setShowDocs] = useState<ApplicationParameterNS.Module | null>(null);
    const [errors, setErrors] = useState<Record<string, true>>({});
    const [autoSelectDepChain, setAutoSelectDepChain] = useState(true);

    useLayoutEffect(() => {
        const listener = async () => {
            const value = valueInput();
            if (value && value.value) {
                const parsed = JSON.parse(value.value) as AppParameterValueNS.ModuleList;
                const newSelected = props.parameter.supportedModules.filter(m => {
                    return parsed.modules.some(it => m.name === it);
                });
                setSelectedModules(newSelected);
            }
        };

        const value = valueInput();
        value!.addEventListener("change", listener);
        return () => {
            value!.removeEventListener("change", listener);
        }
    }, []);

    useEffect(() => {
        const input = valueInput();
        if (input) {
            const value: AppParameterValueNS.ModuleList & {error?: true} = {
                type: "modules",
                modules: selectedModules.map(it => it.name),
            };

            if (Object.keys(errors).length > 0) {
                value["error"] = true;
            }

            input.value = JSON.stringify(value);
        }
    }, [errors, selectedModules]);

    const [supportedModules, modulesByName] = useMemo(() => {
        const modulesByName: Record<string, (ApplicationParameterNS.Module & {nv: NameAndVersion})[]> = {};

        const modules = props.parameter.supportedModules;
        modules.sort((a, b) => {
            const [aName, aVersion] = moduleNameToNameAndVersion(a.name);
            const [bName, bVersion] = moduleNameToNameAndVersion(b.name);

            let cmp = aName.toLowerCase().localeCompare(bName.toLowerCase());
            if (cmp !== 0) return cmp;

            cmp = compareVersionsLoose(aVersion, bVersion);
            if (cmp !== 0) return cmp;

            return 0;
        });

        const deduped: ApplicationParameterNS.Module[] = [];
        for (let i = 0; i < modules.length - 1; i++) {
            const module = modules[i];
            const [moduleName, moduleVersion] = moduleNameToNameAndVersion(module.name);

            const list = modulesByName[moduleName] ?? [];
            list.push({...module, nv: {name: moduleName, version: moduleVersion}});
            modulesByName[moduleName] = list;

            const [nextModuleName] = moduleNameToNameAndVersion(modules[i + 1].name);
            if (moduleName !== nextModuleName) {
                deduped.push(module);
            }
        }

        if (modules.length > 0) {
            deduped.push(modules[modules.length - 1]);
        }

        return [deduped, modulesByName];
    }, [props.parameter.supportedModules]);

    const closeShowDocs = useCallback(() => {
        setShowDocs(null);
    }, []);

    const onAddModule = useCallback((module: ApplicationParameterNS.Module, allowChangeInVersion: boolean = true) => {
        const updated = addOrUpdateModules(modulesByName, selectedModules, module, allowChangeInVersion, autoSelectDepChain);
        setSelectedModules(updated.loadList);
        setErrors(updated.errors);
    }, [modulesByName, selectedModules, autoSelectDepChain]);

    const onRemoveModule = useCallback((module: ApplicationParameterNS.Module) => {
        const newList = selectedModules.filter(it => it.name !== module.name);
        const e = evaluateConfiguration(newList);
        setSelectedModules(newList);
        setErrors(e);
    }, [selectedModules]);

    const toggleAutoSelectDepChain = useCallback(() => {
        setAutoSelectDepChain(p => !p);
    }, [autoSelectDepChain]);

    return (<Flex flexDirection={"column"}>
        <input type="hidden" id={widgetId(props.parameter)} />
        {!error ? null : <>
            <p style={{color: "var(--errorMain)"}}>{error}</p>
        </>}

        <Flex gap={"8px"} flexDirection={"column"}>
            <Box>
                <Table tableType={"presentation"}>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell textAlign={"left"}>Module</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Version</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Dependency conflicts</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"} width={"130px"}>Actions</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {selectedModules.length !== 0 ? null :
                            <TableRow>
                                <TableCell colSpan={4}><i>No modules selected</i></TableCell>
                            </TableRow>
                        }

                        {selectedModules.toSorted((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())).map(((m, idx) => {
                            const [name, version] = moduleNameToNameAndVersion(m.name);
                            return <TableRow key={idx} verticalAlign={"middle"}>
                                <TableCell><HighlightedContainer>{name}</HighlightedContainer></TableCell>
                                <TableCell>
                                    <HighlightedSelected
                                        value={version}
                                        onChange={(ev) => onAddModule(modulesByName[name].find(it => it.nv.version === ev.target.value) ?? m, false)}>
                                        {modulesByName[name].map((it, idx) => <option key={idx}>{it.nv.version}</option>)}
                                    </HighlightedSelected>
                                </TableCell>
                                <TableCell>
                                    {errors[name] ? <>
                                        <div>
                                            Requires one of:
                                        </div>

                                        <ul>
                                            {m.dependsOn.map((chain, idx) => <li key={idx}>{chain.join(", ")}</li>)}
                                        </ul>
                                    </> : <>
                                        <i>No errors</i>
                                    </>}
                                </TableCell>
                                <TableCell>
                                    <Flex gap={"8px"}>
                                        <TooltipV2 tooltip={"Documentation"}>
                                            <Button onClick={() => setShowDocs(m)}>
                                                <Icon name={"heroQuestionMarkCircle"} />
                                            </Button>
                                        </TooltipV2>
                                        <TooltipV2 tooltip={"Remove"}>
                                            <Button color={"errorMain"} onClick={() => onRemoveModule(m)}>
                                                <Icon name={"heroTrash"} />
                                            </Button>
                                        </TooltipV2>
                                    </Flex>
                                </TableCell>
                            </TableRow>;
                        }))}
                    </tbody>
                </Table>
            </Box>

            <Flex flexGrow={1} gap={"16px"} alignItems={"center"}>
                <RichSelect
                    items={supportedModules}
                    keys={["name", "description", "shortDescription", "documentationUrl"]}
                    RenderRow={ModuleRenderRow}
                    RenderSelected={ModuleRenderRow}
                    fullWidth
                    onSelect={onAddModule}
                    selected={undefined}
                />
                <Box flexShrink={0}>
                    <Button color={"successMain"}>
                        <Flex gap={"8px"}>
                            <Icon name={"heroPlus"} />
                            <div>Load module</div>
                        </Flex>
                    </Button>
                </Box>
            </Flex>

            <Flex gap={"8px"} alignItems={"center"} style={{userSelect: "none"}}>
                <Box onClick={toggleAutoSelectDepChain} cursor={"pointer"}>Automatically select dependency chain</Box>
                <Toggle checked={autoSelectDepChain} onChange={toggleAutoSelectDepChain} />
            </Flex>
        </Flex>

        <ReactModal
            isOpen={showDocs != null}
            ariaHideApp={false}
            style={largeModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={closeShowDocs}
            className={CardClass}
        >
            {showDocs ?
                <Box>
                    <Flex gap={"8px"} alignItems={"center"} mb={16}>
                        <AppLogo size={"32px"} hash={hashF(moduleNameToNameAndVersion(showDocs.name)[0])} />
                        <Heading.h2>{showDocs.name}</Heading.h2>
                    </Flex>
                    <Flex flexDirection={"column"}>
                        {!showDocs.documentationUrl ? null :
                            <div>
                                <b>Documentation:</b>
                                {" "}
                                <ExternalLink
                                    href={showDocs.documentationUrl}>{showDocs.documentationUrl}</ExternalLink>
                            </div>
                        }

                        <div>
                            {showDocs.dependsOn.length <= 1 ?
                                <>
                                    <b>Depends on:</b>
                                    {" "}
                                    {showDocs.dependsOn.length === 0 ? "Nothing" : <>{showDocs.dependsOn[0].join(", ")}</>}
                                </>
                                : <>
                                    <b>Depends on (one of):</b>
                                    {" "}
                                    <ul>
                                        {showDocs.dependsOn.map((choice, i) =>
                                            <li key={i}>{choice.join(", ")}</li>
                                        )}
                                    </ul>
                                </>
                            }
                        </div>
                    </Flex>
                    <hr style={{marginBottom: "16px"}} />

                    <ModuleMarkdown>{showDocs.description}</ModuleMarkdown>
                </Box> : null
            }
        </ReactModal>
    </Flex>);
}

export function ModuleMarkdown({children}: React.PropsWithChildren): React.ReactNode {
    return <ReactMarkdown
        components={{
            a: LinkBlock,
            h1: SimpleHeading,
            h2: SimpleHeading,
            h3: SimpleHeading,
            h4: SimpleHeading,
            h5: SimpleHeading,
            h6: SimpleHeading,
        }}
        allowedElements={["h1", "h2", "h3", "h4", "h5", "h6", "br", "a", "p", "strong", "b", "i", "em", "ul", "ol", "li"]}
        children={children as string}
        remarkPlugins={[remarkGfm]}
    />
}

function LinkBlock(props: {href?: string; children: React.ReactNode & React.ReactNode[]}) {
    return <ExternalLink href={props.href}>{props.children}</ExternalLink>;
}

function SimpleHeading(props: {children: React.ReactNode & React.ReactNode[]}) {
    return <Heading.h4>{props.children}</Heading.h4>;
}

const ModuleRenderRow: RichSelectChildComponent<ApplicationParameterNS.Module> = props => {
    const module = props.element;
    if (module == null) {
        return <Flex p={8} height={40}>Select a module from the list</Flex>;
    }

    const [name] = moduleNameToNameAndVersion(module.name);

    return <Flex p={8} height={40} gap={"16px"} {...props.dataProps} onClick={props.onSelect}>
        <AppLogo size={"24px"} hash={hashF(name)} />
        <Box minWidth={"180px"}>{name}</Box>
        <div>{module.shortDescription}</div>
    </Flex>
};

function moduleNameToNameAndVersion(input: string): [string, string] {
    const lastSlash = input.lastIndexOf("/");
    if (lastSlash === -1) {
        return [input, ""];
    }

    return [input.substring(0, lastSlash), input.substring(lastSlash + 1)];
}

const compareVersionsLoose = (a, b) => compareVersions(a, b, true);
const compareVersionsStrict = (a, b) => compareVersions(a, b, false);

function compareVersions(a: string, b: string, missingComponentIsEquality: boolean): number {
    a = a.replace(/version/gi, "");
    b = b.replace(/version/gi, "");

    if (a.length >= 2 && a[0] === "v" && !isNaN(Number(a[1]))) {
        a = a.slice(1);
    }

    if (b.length >= 2 && b[0] === "v" && !isNaN(Number(b[1]))) {
        b = b.slice(1);
    }

    a = a.trim();
    b = b.trim();

    const aTokens = a.split(".");
    const bTokens = b.split(".");
    const maxLen = Math.max(aTokens.length, bTokens.length);

    for (let i = 0; i < maxLen; i++) {
        let aTok = "";
        let bTok = "";

        if (i < aTokens.length) {
            aTok = aTokens[i];
        } else {
            aTok = "0";
            if (missingComponentIsEquality) {
                break;
            }
        }

        if (i < bTokens.length) {
            bTok = bTokens[i];
        } else {
            bTok = "0";
            if (missingComponentIsEquality) {
                break;
            }
        }

        const aSepIdx = aTok.indexOf("-");
        if (aSepIdx !== -1) {
            aTok = aTok.slice(0, aSepIdx);
        }

        const bSepIdx = bTok.indexOf("-");
        if (bSepIdx !== -1) {
            bTok = bTok.slice(0, bSepIdx);
        }

        const aNumeric = parseInt(aTok, 10);
        const bNumeric = parseInt(bTok, 10);

        if (!isNaN(aNumeric) && !isNaN(bNumeric)) {
            if (aNumeric > bNumeric) {
                return 1;
            } else if (aNumeric < bNumeric) {
                return -1;
            }
        } else {
            const cmp = aTok.localeCompare(bTok);
            if (cmp !== 0) {
                return cmp;
            }
        }
    }

    return 0;
}

export const ModuleListValidator: WidgetValidator = (param) => {
    if (param.type === "modules") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true, value: {type: "modules", modules: []}};
        try {
            const parsed = JSON.parse(elem.value) as AppParameterValueNS.ModuleList;
            if ("error" in parsed) {
                return {valid: false, message: "Unable to resolve all dependencies"};
            }

            return {
                valid: true,
                value: parsed,
            };
        } catch (e) {
            return {valid: false, message: "Invalid parameter specified."};
        }
    }
    return {valid: true};
};

export const ModuleListSetter: WidgetSetter = (param, value) => {
    if (param.type !== "modules") return;
    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    const moduleList = value as AppParameterValueNS.ModuleList;
    selector.value = JSON.stringify(moduleList);
    selector.dispatchEvent(new Event("change"));
};
