import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {ResourceBrowser} from "@/ui-components/ResourceBrowser";
import * as React from "react";

function ExperimentalRuns(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);

    const browserRef = React.useRef<ResourceBrowser<Job> | null>(null);
    useTitle("Drives");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            const browser = new ResourceBrowser<Job>(mount, "jobs");
            browserRef.current = browser;
            browser.features = {
                renderSpinnerWhenLoading: true,
                filters: true,
                sortDirection: true,
            }

            browser.on("open", () => { });

            browser.on("fetchFilters", () => [{
                key: "filterCreatedBy",
                text: "Created by",
                type: "input",
                icon: "user"

            },
            {
                key: "filterCreatedAfter",
                options: [],
                text: "Created after",
                type: "options",
                icon: "calendar"
            },
            {
                key: "filterState",
                options: [],
                text: "Status",
                type: "options",
                icon: "radioEmpty"
            }]);

            browser.on("renderRow", (job, row, dims) => {

            });

        }
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<div ref={mountRef} />}
    />;
}