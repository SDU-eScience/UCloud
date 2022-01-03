import * as React from "react";
import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {WSFactory} from "@/Authentication/HttpClientInstance";
import {WebSocketConnection} from "@/Authentication/ws";
import styled from "styled-components";
import {dateToTimeOfDayStringDetailed} from "@/Utilities/DateUtilities";
import {default as ReactJsonView} from "react-json-view";
import {useHistory, useLocation} from "react-router";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {Toggle} from "@/ui-components/Toggle";
import {Icon, Input, Label, Select} from "@/ui-components";
import {formatDuration} from "date-fns";

type MessageImportance = "TELL_ME_EVERYTHING" | "IMPLEMENTATION_DETAIL" | "THIS_IS_NORMAL" | "THIS_IS_ODD" |
    "THIS_IS_WRONG" | "THIS_IS_DANGEROUS";

interface DebugContextBase {
    id: string;
    parent?: string;
}

interface DebugContextServer extends DebugContextBase {
    type: "server";
}

interface DebugContextClient extends DebugContextBase {
    type: "client";
}

interface DebugContextJob extends DebugContextBase {
    type: "job";
}

type DebugContext = DebugContextServer | DebugContextClient | DebugContextJob;

interface DebugMessageBase {
    id: number;
    context: DebugContext;
    timestamp: number;
    principal: {
        username: string;
        role: string;
    };
    importance: MessageImportance;
}

interface DebugMessageClientRequest extends DebugMessageBase {
    type: "client_request";
    call?: string | null;
    payload?: any | null;
    resolvedHost: string;
}

interface DebugMessageClientResponse extends DebugMessageBase {
    type: "client_response";
    call?: string | null;
    response?: any | null;
    responseCode?: number;
}

interface DebugMessageServerRequest extends DebugMessageBase {
    type: "server_request";
    call?: string | null;
    payload?: any | null;
}

interface DebugMessageServerResponse extends DebugMessageBase {
    type: "server_response";
    call?: string | null;
    response?: any | null;
    responseCode: number;
}

interface DebugMessageLog extends DebugMessageBase {
    type: "log";
    message: string;
    extra?: Record<string, any> | null;
}

interface DebugMessageDatabaseTransaction extends DebugMessageBase {
    type: "database_transaction";
    event: "OPEN" | "COMMIT" | "ROLLBACK";
}

interface DebugMessageDatabaseQuery extends DebugMessageBase {
    type: "database_query";
    query: string;
    parameters: Record<string, string>;
}

interface DebugMessageDatabaseResponse extends DebugMessageBase {
    type: "database_response";
}

interface DebugMessageDatabaseConnection extends DebugMessageBase {
    type: "database_connection";
    isOpen: boolean;
}

type DebugMessage = DebugMessageDatabaseTransaction | DebugMessageDatabaseQuery | DebugMessageDatabaseResponse |
    DebugMessageDatabaseConnection | DebugMessageServerRequest | DebugMessageServerResponse |
    DebugMessageClientRequest | DebugMessageClientResponse | DebugMessageLog;

interface DebugSystemListenResponseAppend {
    type: "append";
    messages: DebugMessage[];
}

interface DebugSystemListenResponseClear {
    type: "clear";
}

interface DebugSystemListenResponseAcknowledge {
    type: "ack";
}

type DebugSystemListenResponse = DebugSystemListenResponseAppend | DebugSystemListenResponseClear |
    DebugSystemListenResponseAcknowledge;

function useToggle(initial: boolean): [boolean, () => void] {
    const [active, setActive] = useState(initial);
    const toggle = useCallback(() => {
        setActive(prev => !prev);
    }, []);
    return [active, toggle];
}

function useInput(initial: string): [string, (e: React.SyntheticEvent) => void] {
    const [value, setValue] = useState(initial);
    const onChange = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        setValue(e.target["value"]);
    }, []);

    return [value, onChange];
}

export const Debugger: React.FunctionComponent = () => {
    if (!inDevEnvironment() && !onDevSite()) return null;
    const connRef = useRef<WebSocketConnection | null>(null);
    const [messages, setMessages] = useState<DebugMessage[]>([]);
    const scrollingContainer = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const history = useHistory();
    const inspectingId = getQueryParam(location.search, "inspecting")
    const inspectingUid = getQueryParam(location.search, "inspectingUid")
    const inspecting = useMemo(
        () => inspectingUid == null ? null : messages.find(it => it.id === parseInt(inspectingUid)),
        [inspectingUid, messages]
    );
    const setInspecting = useCallback((message: DebugMessage) => {
        history.push("/debugger?hide-frame&inspecting=" + message.context.id + "&inspectingUid=" + message.id);
    }, []);

    const doClear = useCallback(() => {
        const conn = connRef.current;
        history.push("/debugger?hide-frame");
        if (conn) {
            conn.call({
                call: "debug.listen",
                payload: {type: "clear"}
            });
        }
    }, []);

    const [overviewShowServer, overviewToggleShowServer] = useToggle(true);
    const [overviewShowClient, overviewToggleShowClient] = useToggle(false);
    const [overviewShowDatabase, overviewToggleShowDatabase] = useToggle(false);
    const [overviewShowLogs, overviewToggleShowLogs] = useToggle(false);
    const [overviewQuery, overviewOnQueryUpdate] = useInput("");
    const [overviewLogLevel, overviewOnLogLevelUpdate] = useInput("THIS_IS_NORMAL");

    const [inspectingShowServer, inspectingToggleShowServer] = useToggle(true);
    const [inspectingShowClient, inspectingToggleShowClient] = useToggle(true);
    const [inspectingShowDatabase, inspectingToggleShowDatabase] = useToggle(true);
    const [inspectingShowLogs, inspectingToggleShowLogs] = useToggle(true);
    const [inspectingQuery, inspectingOnQueryUpdate] = useInput("");
    const [inspectingLogLevel, inspectingOnLogLevelUpdate] = useInput("IMPLEMENTATION_DETAIL");

    const [showServer, toggleShowServer] = inspectingId ?
        [inspectingShowServer, inspectingToggleShowServer] : [overviewShowServer, overviewToggleShowServer];
    const [showClient, toggleShowClient] = inspectingId ?
        [inspectingShowClient, inspectingToggleShowClient] : [overviewShowClient, overviewToggleShowClient];
    const [showDatabase, toggleShowDatabase] = inspectingId ?
        [inspectingShowDatabase, inspectingToggleShowDatabase] : [overviewShowDatabase, overviewToggleShowDatabase];
    const [showLogs, toggleShowLogs] = inspectingId ?
        [inspectingShowLogs, inspectingToggleShowLogs] : [overviewShowLogs, overviewToggleShowLogs];
    const [query, onQueryUpdate] = inspectingId ?
        [inspectingQuery, inspectingOnQueryUpdate] : [overviewQuery, overviewOnQueryUpdate];
    const [logLevel, onLogLevelUpdate] = inspectingId ?
        [inspectingLogLevel, inspectingOnLogLevelUpdate] : [overviewLogLevel, overviewOnLogLevelUpdate];

    useEffect(() => {
        const conn = connRef.current;

        const types = [] as string[];
        if (showServer) types.push("SERVER");
        if (showClient) types.push("CLIENT");
        if (showDatabase) types.push("DATABASE");
        if (showLogs) types.push("LOG")

        const ids = inspectingId ? [inspectingId] : null;

        if (conn != null) {
            conn.call({
                call: "debug.listen",
                payload: {
                    type: "context_filter",
                    ids,
                    minimumLevel: logLevel,
                    query,
                    types
                }
            });
        }
    }, [inspectingId, showServer, showClient, showDatabase, logLevel, query]);

    const handleMessage = useCallback((message: DebugSystemListenResponse) => {
        switch (message.type) {
            case "append": {
                setMessages(prev => [...prev, ...message.messages]);
                const c = scrollingContainer.current;
                if (c) c.scrollTo(0, c.scrollHeight);
                break;
            }
            case "clear":
                setMessages([]);
                break;
            case "ack":
                // Do nothing
                break;
        }
    }, []);

    useEffect(() => {
        const wsConnection = WSFactory.open("/debug", {
            init: async conn => {
                await conn.subscribe({
                    call: "debug.listen",
                    payload: {type: "init"},
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as DebugSystemListenResponse;
                            handleMessage(payload);
                        }
                    }
                });
            }
        });

        connRef.current = wsConnection;

        return () => wsConnection.close();
    }, []);

    return <DebugStyle>
        <div className="control">
            <div>
                <b onClick={toggleShowServer}>Server:</b><br/>
                <Toggle onChange={toggleShowServer} checked={showServer}/>
            </div>

            <div>
                <b onClick={toggleShowClient}>Client:</b><br/>
                <Toggle onChange={toggleShowClient} checked={showClient}/>
            </div>

            <div>
                <b onClick={toggleShowDatabase}>Database:</b><br/>
                <Toggle onChange={toggleShowDatabase} checked={showDatabase}/>
            </div>

            <div>
                <b onClick={toggleShowLogs}>Logs:</b><br/>
                <Toggle onChange={toggleShowLogs} checked={showLogs}/>
            </div>

            <Label>
                Search<br/>
                <Input value={query} onChange={onQueryUpdate} />
            </Label>

            <Label>
                Log level<br/>
                <Select value={logLevel} onChange={onLogLevelUpdate}>
                    <option value="TELL_ME_EVERYTHING">Tell me everything</option>
                    <option value="IMPLEMENTATION_DETAIL">Implementation details</option>
                    <option value="THIS_IS_NORMAL">This is normal</option>
                    <option value="THIS_IS_ODD">This is odd</option>
                    <option value="THIS_IS_DANGEROUS">This is dangerous</option>
                </Select>
            </Label>

            <Icon cursor={"pointer"} name={"trash"} onClick={doClear} size={32} mt={23} color={"red"}/>
        </div>
        <div className={"stats"}>
            <div>{messages.length} messages in-view</div>
            <div>
                {messages.length > 0 ?
                    formatDuration({
                        seconds: (messages[messages.length - 1].timestamp -  messages[0].timestamp) / 1000.0
                    }) : null
                }
            </div>
        </div>

        {inspecting == null ? null :
            <div className="inspecting">
                <ReactJsonView src={inspecting} name={false} collapseStringsAfterLength={360} displayDataTypes={false} groupArraysAfterLength={10} />
                {inspecting.type !== "database_query" ? null : <>
                    <pre><code>{inspecting.query}</code></pre>
                </>}
            </div>
        }

        <div className={`inner ${inspecting == null ? "no-inspect" : "inspect"}`} ref={scrollingContainer}>
            {messages.map((m, idx) => (
                <MessageRow message={m} key={idx} onClick={setInspecting}/>
            ))}
        </div>
    </DebugStyle>;
};

const DebugStyle = styled.div`
  margin: 16px;

  .control {
    height: 70px;
    overflow-y: auto;

    display: flex;
    gap: 16px;
  }
  
  .stats {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
  }

  .inspecting {
    display: flex;
    height: 500px;
    overflow-y: auto;
    margin-bottom: 16px;
  }

  .inner {
    overflow-y: auto;
    overflow-x: hidden;
  }

  .inner.no-inspect {
    height: calc(100vh - 150px);
  }

  .inner.inspect {
    height: calc(100vh - 650px);
  }

  .row {
    display: flex;
    gap: 16px;
    cursor: pointer;
  }

  .type, .timestamp {
    width: 130px;
  }
`;

const MessageRow: React.FunctionComponent<{
    message: DebugMessage;
    onClick: (message: DebugMessage) => void;
}> = ({message, onClick}) => {
    const actualOnClick = useCallback(() => {
        onClick(message);
    }, [message, onClick]);
    return <div className={"row"} onClick={actualOnClick}>
        <div className="type">
            {message.type !== "database_connection" ? null :
                message.isOpen ? "DB Open" : "DB Close"
            }
            {message.type !== "database_query" ? null : "DB Query"}
            {message.type !== "database_response" ? null : "DB Response"}
            {message.type !== "database_transaction" ? null :
                message.event === "OPEN" ? "DB Begin" :
                    message.event === "COMMIT" ? "DB Commit" :
                        message.event === "ROLLBACK" ? "DB Rollback" : null
            }
            {message.type !== "server_request" ? null : "Server Request"}
            {message.type !== "server_response" ? null : "Server Response"}
            {message.type !== "client_request" ? null : "Client Request"}
            {message.type !== "client_response" ? null : "Client Response"}
            {message.type !== "log" ? null : "Log"}
        </div>
        <div className={"timestamp"}>
            {dateToTimeOfDayStringDetailed(message.timestamp)}
        </div>
        {"call" in message && !!message["call"] ? <div>{message["call"]}</div> : null}
        <div>{message.importance}</div>
        {message.type === "log" ? message.message : null}
        {message.type === "server_response" || message.type === "client_response" ?
            <div>{message.responseCode}</div> : null}
    </div>;
}
