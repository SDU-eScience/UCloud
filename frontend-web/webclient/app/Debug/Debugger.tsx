import * as React from "react";
import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";
import {Button, ExternalLink} from "@/ui-components";

export const Debugger: React.FunctionComponent = () => {
    if (!inDevEnvironment() && !onDevSite()) return null;
    return <>
        <div>
            The debugger has no moved into its own separate project. It is automatically started when running 
            the <code>./run.sh</code> script from the <code>backend</code>. If the debugger is not working as intended,
            then you might want to look at the <code>/tmp/debugger.log</code> log file (from the <code>backend</code>).
            You can also (re)start the debugger with <code>./start_debugger.sh</code> script.
        </div>
        <div>
            <ExternalLink href="https://debugger.localhost.direct">
                <Button>Open debugger - I will read the text above if it is not working</Button>
            </ExternalLink>
        </div>
    </>;
};

