import * as React from "react";
import {useParams} from "react-router-dom";

import MainContainer from "@/ui-components/MainContainer";
import {useCloudAPI} from "@/Authentication/DataHook";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";

import * as StackApi from "./api";

export default function StackView(): React.ReactNode {
    const {id} = useParams<{id: string}>();
    const [stackState, fetchStack] = useCloudAPI<StackApi.Stack | null>({noop: true}, null);

    usePage("Stack", SidebarTabId.RUNS);

    React.useEffect(() => {
        if (!id) return;
        fetchStack(StackApi.retrieve({id}));
    }, [id, fetchStack]);

    const stack = stackState.data;

    return <MainContainer
        main={
            <div>
                <h2>Stack details</h2>
                {!id ? <p>Missing stack ID.</p> : null}
                {id && stackState.loading ? <p>Loading stack...</p> : null}
                {id && stackState.error ? <p>Could not load stack: {stackState.error.why}</p> : null}
                {id && !stackState.loading && !stackState.error && !stack ? <p>Stack not found.</p> : null}
                {stack ? <pre>{JSON.stringify(stack, null, 2)}</pre> : null}
            </div>
        }
    />;
}
