import * as React from "react";
import {useCallback, useEffect, useMemo} from "react";
import MainContainer from "@/MainContainer/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiBrowse, apiUpdate, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {PageV2} from "@/UCloud";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {Flex, Icon, List} from "@/ui-components";
import {ItemRenderer, ItemRow} from "@/ui-components/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {Operation, Operations} from "@/ui-components/Operation";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {formatDistanceToNow} from "date-fns";

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

const Scripts: React.FunctionComponent = () => {
    const [scripts, fetchScripts] = useCloudAPI<PageV2<ScriptInfo>>({noop: true}, emptyPageV2);

    const toggleSet = useToggleSet(scripts.data.items);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const reload = useCallback(() => {
        if (Client.userIsAdmin) fetchScripts(apiBrowse({itemsPerPage: 50}, baseContext));
        toggleSet.uncheckAll();
    }, []);

    useTitle("Scripts");
    useRefreshFunction(reload);
    useEffect(() => reload(), [reload]);

    const callbacks: Callbacks = useMemo(() => ({
        invokeCommand,
        reload
    }), [reload]);

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={
            <>
                <Operations location={"TOPBAR"} operations={operations} selected={toggleSet.checked.items}
                    extra={callbacks} entityNameSingular={"Script"} />
                <List>
                    {scripts.data.items.map(it =>
                        <ItemRow
                            key={it.metadata.id}
                            browseType={BrowseType.MainContent}
                            renderer={scriptRenderer}
                            toggleSet={toggleSet}
                            operations={operations}
                            callbacks={callbacks}
                            itemTitle={"Script"}
                            item={it}
                        />
                    )}
                </List>
            </>
        }
    />;
};

const scriptRenderer: ItemRenderer<ScriptInfo> = {
    Icon: props => {
        return <Icon name={"play"} size={props.size} />
    },

    MainTitle: props => {
        return <>{props.resource?.metadata?.title}</>
    },

    ImportantStats: props => {
        if (props.resource == null) return null;
        return <Flex alignItems={"center"} mx={32} flexShrink={0}>
            Last run: {props.resource.lastRun === 0 ? "Never" : formatDistanceToNow(props.resource.lastRun)}
            {" "}ago
        </Flex>;
    }
};

interface Callbacks {
    invokeCommand: InvokeCommand;
    reload: () => void;
}

const operations: Operation<ScriptInfo, Callbacks>[] = [
    {
        text: "Start",
        icon: "play",
        primary: true,
        enabled: (selected) => selected.length >= 1,
        onClick: async (selected, cb) => {
            await cb.invokeCommand(
                apiUpdate(bulkRequestOf(...selected.map(it => ({scriptId: it.metadata.id}))), baseContext, "start")
            );
            cb.reload();
        }
    }
];

export default Scripts;