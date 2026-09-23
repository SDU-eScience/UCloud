import * as React from "react";
import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {callAPI} from "@/Authentication/DataHook";
import {stateToTitle} from "@/Applications/Jobs";
import JobsApi from "@/UCloud/JobsApi";
import {dateToString} from "@/Utilities/DateUtilities";
import AppRoutes from "@/Routes";
import {compute} from "@/UCloud";
import {Box, Flex, Icon} from "@/ui-components";
import {injectStyle} from "@/Unstyled";

const JobList = injectStyle("private-network-job-list", k => `
    ${k} > a {
        display: block;
        padding: 12px 0;
    }
    ${k} > a:not(:last-child) {
        border-bottom: 1px solid var(--borderColor);
    }
`);

interface JobEntry {
    id: string;
    name: string;
    state: string;
    createdAt?: number;
    ips: string[];
}

export function ActiveNetworkJobs({members, networkId}: {members: string[], networkId: string}): React.ReactNode {
    const [jobs, setJobs] = useState<JobEntry[]>([]);

    useEffect(() => {
        let active = true;
        setJobs([]);
        Promise.all(members.map(async id => {
            try {
                const job = await callAPI(JobsApi.retrieve({id}));
                const networkBinding = job.specification.resources?.find(resource =>
                    resource.type === "private_network" && resource.id === networkId
                ) as compute.AppParameterValueNS.PrivateNetwork | undefined;
                return {
                    id,
                    name: job.specification.name || `Job ${id}`,
                    state: stateToTitle(job.status.state),
                    createdAt: job.createdAt,
                    ips: networkBinding?.ips ?? [],
                };
            } catch {
                return {id, name: `Job ${id}`, state: "Unavailable", ips: [] as string[]};
            }
        })).then(entries => {
            if (active) setJobs(entries);
        });
        return () => { active = false; };
    }, [members, networkId]);

    return <div className={JobList}>
        {jobs.length === 0 ? <Box color="textSecondary" py="12px">
            {members.length === 0 ? "No active jobs are connected." : "Loading active jobs..."}
        </Box> : jobs.map(job =>
            <Link key={job.id} to={AppRoutes.jobs.view(job.id)}>
                <Flex gap="12px" alignItems="center" flexWrap="wrap">
                    <Icon name="heroCpuChip" size={20} />
                    <Box flexGrow={1}>{job.name}</Box>
                    {job.ips.length > 0 ? <Box color="textSecondary">{job.ips.join(", ")}</Box> : null}
                    <Box color="textSecondary">{job.state}</Box>
                    {job.createdAt ? <Box color="textSecondary">{dateToString(job.createdAt)}</Box> : null}
                    <Icon name="heroArrowRight" size={16} />
                </Flex>
            </Link>)}
    </div>;
}
