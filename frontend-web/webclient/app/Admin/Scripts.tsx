import * as React from "react";
import MainContainer from "@/ui-components/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiBrowse, apiUpdate, callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {PageV2} from "@/UCloud";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {format} from "date-fns";
import {ResourceBrowseFeatures, ResourceBrowser, ColumnTitleList} from "@/ui-components/ResourceBrowser";
import {DATE_FORMAT} from "./NewsManagement";
import {ButtonClass} from "@/ui-components/Button";
import {createHTMLElements} from "@/UtilityFunctions";

const baseContext = "/api/scripts";

type WhenToStart = WhenToStartPeriodically | WhenToStartDaily | WhenToStartWeekly;

interface WhenToStartPeriodically {
    type: "periodically";
    timeBetweenInvocationInMillis: number;
}

interface WhenToStartDaily {
    type: "daily";
    hour: number;
    minute: number;
}

interface WhenToStartWeekly {
    type: "weekly";
    dayOfWeek: number;
    hour: number;
    minute: number;
}

interface ScriptMetadata {
    id: string;
    title: string;
    whenToStart: WhenToStart;
}

interface ScriptInfo {
    metadata: ScriptMetadata;
    lastRun: number;
}

const FEATURES: ResourceBrowseFeatures = {
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
    renderSpinnerWhenLoading: true,
};

const ROW_TITLES: ColumnTitleList = [{name: "Script"}, {name: ""}, {name: "Last run"}, {name: ""}];
function Scripts(): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ScriptInfo> | null>(null);
    useTitle("Scripts");
    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<ScriptInfo>(mount, "Scripts", {}).init(browserRef, FEATURES, "", browser => {
                browser.setColumnTitles(ROW_TITLES);
                browser.on("open", (oldPath, newPath, res) => {
                    if (res) return;
                    callAPI(apiBrowse({itemsPerPage: 250}, baseContext)).then((it: PageV2<ScriptInfo>) => {
                        browser.registerPage(it, newPath, true);
                        browser.renderRows()
                    });
                })

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(apiBrowse({
                        next: browser.cachedNext[path] ?? undefined,
                    }, baseContext));
                    browser.registerPage(result, "", false);
                });

                browser.setEmptyIcon("play");
                browser.on("pathToEntry", entry => entry.metadata.title);
                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("fetchOperationsCallback", () => ({}));
                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    return operations.filter(it => it.enabled(entries, {}, entries))
                });
                browser.on("renderEmptyPage", reason => browser.defaultEmptyPage("scripts", reason, {}));
                browser.on("unhandledShortcut", listener => void 0);

                browser.on("renderRow", (script, row, dim) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);
                    browser.icons.renderIcon({
                        name: "play",
                        color: "black",
                        color2: "black",
                        height: 32,
                        width: 32,
                    }).then(setIcon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(script.metadata.title, dim, row));

                    const lastRun = script.lastRun === 0 ? "Never" : format(script.lastRun, DATE_FORMAT)
                    const textNode = document.createTextNode(lastRun);
                    row.stat2.append(textNode);

                    const runButton = createHTMLElements({
                        tagType: "button",
                        style: {height: "32px", width: "64px"},
                        className: ButtonClass,
                        handlers: {
                            onClick(e) {
                                e.stopImmediatePropagation();
                                const [start] = operations;
                                if (start.text !== "Start") {
                                    console.warn("Something went wrong. This is the wrong operation.")
                                    return;
                                }
                                start.onClick([script], {});
                            }
                        }
                    });
                    runButton.innerText = "Run";
                    row.stat3.append(runButton);
                });
            })
        }
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    if (!Client.userIsAdmin) return null;

    return <MainContainer main={<div ref={mountRef} />} />
}

const operations: Operation<ScriptInfo, Record<string, never>>[] = [
    {
        text: "Start",
        icon: "play",
        primary: true,
        enabled: (selected) => selected.length >= 1,
        onClick: async (selected) => {
            await callAPI(
                apiUpdate(bulkRequestOf(...selected.map(it => ({scriptId: it.metadata.id}))), baseContext, "start")
            );
        },
        shortcut: ShortcutKey.S
    }
];

export default Scripts;
