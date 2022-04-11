import * as React from "react";
import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {WSFactory} from "@/Authentication/HttpClientInstance";
import {WebSocketConnection} from "@/Authentication/ws";
import styled from "styled-components";
import {dateToTimeOfDayStringDetailed} from "@/Utilities/DateUtilities";
import {useHistory, useLocation} from "react-router";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {Toggle} from "@/ui-components/Toggle";
import {Icon, Input, Label, Select} from "@/ui-components";
import {formatDuration} from "date-fns";
import { BigJsonViewerDom } from "big-json-viewer";
import { buildQueryString } from "@/Utilities/URIUtilities";


type MessageImportance = "TELL_ME_EVERYTHING" | "IMPLEMENTATION_DETAIL" | "THIS_IS_NORMAL" | "THIS_IS_ODD" |
    "THIS_IS_WRONG" | "THIS_IS_DANGEROUS";

interface DebugContextBase {
    id: string;
    parent?: string;
    depth: number;
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
    const messagesRef = useRef<DebugMessage[]>([]);
    const messages = messagesRef.current;
    const scrollingContainer = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const history = useHistory();
    const inspectingId = getQueryParam(location.search, "inspecting");
    const inspectingUid = getQueryParam(location.search, "inspectingUid");
    const inspectingChildUid = getQueryParam(location.search, "inspectingChild");
    const inspecting = useMemo(
        () => inspectingUid == null ? null : messages.find(it => it.id === parseInt(inspectingUid)),
        [inspectingUid, messages]
    );
    const inspectingChild = useMemo(
        () => inspectingChildUid == null ? null : messages.find(it => it.id === parseInt(inspectingChildUid)),
        [inspectingChildUid, messages]
    );
    const setInspecting = useCallback((message: DebugMessage) => {
        const isChild = !!message.context.parent;
        const params = {};
        params["hide-frame"] = "yes";

        if (isChild) {
            const inspectingId = getQueryParam(window.location.search, "inspecting");
            const inspectingUid = getQueryParam(window.location.search, "inspectingUid");

            params["inspecting"] = inspectingId;
            params["inspectingUid"] = inspectingUid;
            params["inspectingChild"] = message.id;
        } else {
            params["inspecting"] = message.context.id;
            params["inspectingUid"] = message.id;
        }

        history.push(buildQueryString("/debugger", params));
    }, []);

    const goHome = useCallback(() => {
        history.push("/debugger?hide-frame");
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
    const [overviewShowLogs, overviewToggleShowLogs] = useToggle(true);
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

    const [messagesInView, setMessagesInView] = useState(0);
    const [durationInView, setDurationInView] = useState(0);

    useEffect(() => {
        const inFocus = inspectingChild ?? inspecting;

        const allRows = document.querySelectorAll(".row");
        allRows.forEach(it => it.classList.remove("active"));

        const queryContainer = document.querySelector(".db-query");
        if (queryContainer) queryContainer.textContent = "";

        const main = document.querySelector(".debugger-main");
        if (main) {
            main.classList.remove("no-inspect");
            main.classList.remove("inspect");

            if (inFocus) main.classList.add("inspect");
            else main.classList.add("no-inspect");
        }

        if (inFocus) {
            BigJsonViewerDom.fromObject(inFocus).then(viewer => {
                const container = document.querySelector(".json-inspector");
                if (container) {
                    container.innerHTML = "";

                    const root = viewer.getRootElement();
                    root.openAll(3);
                    container.prepend(root);
                }

                if (inFocus.type === "database_query" && queryContainer) {
                    queryContainer.textContent = inFocus.query;
                }

                // NOTE(Dan): Kind of just hoping that the row has been rendered by the time BigJsonViewerDom is done.
                const relevantRow = document.querySelector(`.row[data-row-uid='${inFocus.id}']`);
                relevantRow?.classList.add("active");
            });
        }
    }, [inspecting, inspectingChild]);

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
                    types,
                    requireTopLevel: !inspectingId
                }
            });
        }
    }, [inspectingId, showServer, showClient, showDatabase, showLogs, logLevel, query]);

    useEffect(() => {
        console.log("inspecting id changed");
    }, [inspectingId]);

    const handleMessage = useCallback((message: DebugSystemListenResponse) => {
        switch (message.type) {
            case "append": {
                const container = scrollingContainer.current;
                if (container) {
                    // NOTE(Dan): Use a fragment to reduce the number of reflows
                    const fragment = document.createDocumentFragment();
                    const allMessages = messagesRef.current;
                    for (const m of message.messages) {
                        fragment.appendChild(buildMessageRow(m, setInspecting));
                        allMessages.push(m);
                    }
                    container.appendChild(fragment);
                    container.scrollTo(0, container.scrollHeight);

                    setMessagesInView(allMessages.length);
                    setDurationInView(allMessages[allMessages.length - 1].timestamp - allMessages[0].timestamp);
                }
                break;
            }
            case "clear":
                const container = scrollingContainer.current;
                if (container) {
                    container.innerHTML = "";
                }
                const allMessages = messagesRef.current;
                allMessages.length = 0;

                setMessagesInView(0);
                setDurationInView(0);
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

            <Icon cursor={"pointer"} name={"home"} onClick={goHome} size={32} mt={23} color={"red"}/>
            <Icon cursor={"pointer"} name={"trash"} onClick={doClear} size={32} mt={23} color={"red"}/>
        </div>
        <div className={"stats"}>
            <div>{messagesInView} messages in-view</div>
            <div>
                {messagesInView > 0 ?
                    formatDuration({seconds: (durationInView) / 1000.0}) : null
                }
            </div>
        </div>

        <div className="debugger-main">
            <div className="inspecting">
                <div className="json-inspector" />
                <pre><code className="db-query"></code></pre>
            </div>

            <div className="inner" ref={scrollingContainer} />
        </div>
    </DebugStyle>;
};

function messageToType(message: DebugMessage): string {
    switch (message.type) {
        case "database_query": return "DB Query";
        case "database_response": return "DB Response";
        case "database_connection": {
            if (message.isOpen) return "DB Open";
            else return "DB Close";
        }
        case "database_transaction": {
            switch (message.event) {
                case "OPEN": return "DB Begin";
                case "COMMIT": return "DB Commit";
                case "ROLLBACK": return "DB Rollback";
            }
        }

        case "server_request": return "Server request";
        case "server_response": return "Server response";

        case "client_request": return "Client request";
        case "client_response": return "Client response";

        case "log": return "Log";
    }
}

function shortDebugMessage(message: DebugMessage): string | null {
    let result = "";
    if ("call" in message && !!message["call"]) result += message.call;
    if (message.type === "log") result += message.message;
    if (message.type === "server_response" || message.type === "client_response") {
        result += " " + (message.responseCode?.toString() ?? "");
    }
    return !result ? null : result;
}

function buildMessageRow(message: DebugMessage, onClick: (message: DebugMessage) => void): HTMLElement {
    function text(txt: string): Text {
        return document.createTextNode(txt);
    }

    const row = document.createElement("div");
    {
        row.className = "row";
        row.onclick = () => onClick(message);
        row.setAttribute("data-row-uid", message.id.toString());
        row.style.marginLeft = `${(message.context.depth ?? 0) * 15}px`;
    }

    {
        const type = document.createElement("div");
        type.className = "type";
        type.appendChild(text(messageToType(message)));

        row.appendChild(type);
    }

    {
        const timestamp = document.createElement("div");
        timestamp.className = "timestamp";
        timestamp.appendChild(text(dateToTimeOfDayStringDetailed(message.timestamp)));

        row.appendChild(timestamp);
    }

    {
        const shortMesage = shortDebugMessage(message);
        if (shortMesage) {
            const msg = document.createElement("div");
            msg.appendChild(text(shortMesage));
            row.appendChild(msg);
        }
    }

    return row;
}

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

  .no-inspect .inner {
    height: calc(100vh - 150px);
  }

  .inspect .inner {
    height: calc(100vh - 650px);
  }

  .no-inspect .inspecting {
    display: none;
  }

  .row {
    display: flex;
    gap: 16px;
    cursor: pointer;
  }

  .row.active {
    background-color: var(--lightBlue);
  }

  .type, .timestamp {
    width: 130px;
  }

.json-node-type {
  color: #7f7f7f;
}

.json-node-toggler,
.json-node-stub-toggler {
  text-decoration: none;
  color: inherit;
}

.json-node-toggler:hover,
.json-node-stub-toggler:hover {
  background: #e2e2e2;
}

.json-node-children,
.json-node-root {
  padding-left: 1em;
}

.json-node-header mark {
  background-color: rgba(199, 193, 0, 0.5);
  padding: 0;
}

.json-node-header mark.highlight-active {
  background-color: rgba(199, 103, 46, 0.5);
}

.json-node-label {
  color: #184183;
}

.json-node-number .json-node-value {
  color: blue;
}

.json-node-string .json-node-value {
  color: green;
}

.json-node-boolean .json-node-value {
  color: #95110f;
}

.json-node-null .json-node-value {
  color: #959310;
}

.json-node-number .json-node-type,
.json-node-string .json-node-type,
.json-node-boolean .json-node-type,
.json-node-undefined .json-node-type,
.json-node-null .json-node-type {
  display: none;
}

.json-node-accessor {
  position: relative;
}

.json-node-accessor::before {
  position: absolute;
  content: '▶';
  font-size: 0.6em;
  line-height: 1.6em;
  right: 0.5em;
  transition: transform 100ms ease-out;
}

.json-node-open .json-node-accessor::before {
  transform: rotate(90deg);
}

.json-node-children {
  /*animation-duration: 500ms;*/
  /*animation-name: json-node-children-open;*/
  /*transform-origin: top;*/
}

.json-node-stub-toggler .json-node-label,
.json-node-collapse {
  color: #7f7f7f;
}
.json-node-collapse {
  font-size: 0.8em;
}

@keyframes json-node-children-open {
  from {
    transform: scaleY(0);
  }
  to {
    transform: scaleY(1);
  }
}

.json-node-link {
  display: none;
  padding-left: 0.5em;
  font-size: 0.8em;
  color: #7f7f7f;
  text-decoration: none;
}

.json-node-link:hover {
  color: black;
}

.json-node-header:hover .json-node-link {
  display: inline;
}
`;

