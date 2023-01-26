import {isSocketOpen} from "../WebSockets/Socket";

export function Header({children}: {children: React.ReactNode}): JSX.Element {
    const connected = isSocketOpen();
    return <div className="header">
        <span className="vertically-centered ucloud-title white-text"><b>UCloud</b></span>
        {connected ? null : <div className="connection vertically-centered ucloud-title red-background white-text">Not connected to backend</div>}
        <div className="flex auto-margin">
            {children}
        </div>
    </div>
}