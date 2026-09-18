import * as React from "react";
import {useNavigate} from "react-router-dom";
import {VirtualizedTree} from "@/ui-components/VirtualizedTree";
import {Box, Flex, Icon, Truncate} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Text from "@/ui-components/Text";
import {injectStyle} from "@/Unstyled";
import {dialogStore} from "@/Dialog/DialogStore";
import {shortUUID} from "@/UtilityFunctions";
import {stateToTitle} from "@/Applications/Jobs";
import AppRoutes from "@/Routes";
import {IconName} from "@/ui-components/Icon";
import {IconButton} from "@/ui-components/IconButton";

import {StackStatus} from "./api";

interface StackResourceTypeNode {
    kind: "type";
    id: string;
    title: string;
    icon: IconName;
    count: number;
}

interface StackResourceEntryNode {
    kind: "entry";
    id: string;
    href: string;
    externalHref?: string;
    title: string;
    subtitle: string;
    icon: IconName;
}

type StackResourceNode = StackResourceTypeNode | StackResourceEntryNode;

export function StackResourcesDialog({status}: {status: StackStatus}): React.ReactNode {
    const navigate = useNavigate();

    const groups: {id: string; title: string; icon: IconName; entries: StackResourceEntryNode[]}[] = [
        {
            id: "jobs",
            title: "Jobs",
            icon: "heroCpuChip",
            entries: (status.jobs ?? []).map(job => ({
                kind: "entry" as const,
                id: job.id,
                href: AppRoutes.jobs.view(job.id),
                title: job.specification.name ?? shortUUID(job.id),
                subtitle: stateToTitle(job.status.state),
                icon: "heroCpuChip" as IconName,
            })),
        },
        {
            id: "publicLinks",
            title: "Public links",
            icon: "heroGlobeEuropeAfrica",
            entries: (status.publicLinks ?? []).map(link => ({
                kind: "entry" as const,
                id: link.id,
                href: AppRoutes.resource.properties("public-links", link.id),
                externalHref: `https://${link.specification.domain.replace(/^https?:\/\//, "")}`,
                title: link.specification.domain,
                subtitle: link.status.state,
                icon: "heroGlobeEuropeAfrica" as IconName,
            })),
        },
        {
            id: "publicIps",
            title: "Public IPs",
            icon: "heroWifi",
            entries: (status.publicIps ?? []).map(ip => ({
                kind: "entry" as const,
                id: ip.id,
                href: AppRoutes.resource.properties("public-ips", ip.id),
                title: ip.status.ipAddress ?? "Pending",
                subtitle: ip.status.state,
                icon: "heroWifi" as IconName,
            })),
        },
        {
            id: "networks",
            title: "Private networks",
            icon: "heroCloud",
            entries: (status.networks ?? []).map(network => ({
                kind: "entry" as const,
                id: network.id,
                href: AppRoutes.resource.properties("private-networks", network.id),
                title: network.specification.name || shortUUID(network.id),
                subtitle: network.specification.subdomain || "",
                icon: "heroCloud" as IconName,
            })),
        },
        {
            id: "licenses",
            title: "Licenses",
            icon: "heroKey",
            entries: (status.licenses ?? []).map(license => ({
                kind: "entry" as const,
                id: license.id,
                href: AppRoutes.resource.properties("licenses", license.id),
                title: license.specification.product.id,
                subtitle: license.status.state,
                icon: "heroKey" as IconName,
            })),
        },
    ];

    const allNodes: StackResourceNode[] = [];
    let total = 0;
    for (const group of groups) {
        if (group.entries.length === 0) continue;
        total += group.entries.length;
        const typeId = `type-${group.id}`;
        allNodes.push({kind: "type", id: typeId, title: group.title, icon: group.icon, count: group.entries.length});
        for (const entry of group.entries) {
            allNodes.push({...entry, id: `${typeId}:${entry.id}`});
        }
    }

    const rootNodeNodes: StackResourceNode[] = allNodes.filter(node => node.kind === "type");

    const childrenOf = React.useCallback((node: StackResourceNode): readonly StackResourceNode[] => {
        if (node.kind !== "type") return [];
        return allNodes.filter(candidate => candidate.kind === "entry" && candidate.id.startsWith(node.id + ":"));
    }, [allNodes]);

    return <div className={StackResourcesDialogClass} onKeyDown={e => e.stopPropagation()}>
        <Flex alignItems="center" gap="8px">
            <Heading.h3>Resources in stack</Heading.h3>
            <Box flexGrow={1} />
            <Text color="textSecondary">{total} resources</Text>
        </Flex>

        {allNodes.length === 0 ?
            <Text color="textSecondary">This stack contains no resources.</Text> :
            <div className="tree-container">
                <VirtualizedTree
                    label="Stack resources"
                    nodes={rootNodeNodes}
                    getId={node => node.id}
                    getChildren={childrenOf}
                    isBranch={node => node.kind === "type"}
                    rowHeight={44}
                    ariaLabel={node => node.title}
                    renderNode={(node, state) => (
                        <StackResourceRow node={node} expanded={state.expanded} onToggle={state.toggle} />
                    )}
                    onActivate={node => {
                        if (node.kind !== "entry") return;
                        openEntry(node);
                    }}
                />
            </div>}
    </div>;
}

const StackResourceRow: React.FunctionComponent<{
    node: StackResourceNode;
    expanded: boolean;
    onToggle(): void;
}> = ({node, expanded, onToggle}) => {
    const navigate = useNavigate();
    const isType = node.kind === "type";

    return <Flex
        alignItems="center"
        gap="8px"
        width="100%"
        mr={"16px"}
        minWidth={0}
        cursor={isType ? "pointer" : undefined}
        onClick={isType ? onToggle : undefined}
    >
        {isType ? <Icon
            name="heroChevronDown"
            size={14}
            color="textPrimary"
            rotation={expanded ? 0 : -90}
            cursor="pointer"
            onClick={e => {
                e.stopPropagation();
                onToggle();
            }}
        /> : <Box width="22px" flexShrink={0} />}
        <Icon name={node.icon} size={16} color={isType ? "textPrimary" : "iconColor"} />
        <Box minWidth={0} flexGrow={1}>
            <Truncate width="100%" title={node.title}>{node.title}</Truncate>
            {!isType && node.subtitle ?
                <Text fontSize="11px" color="textSecondary">{node.subtitle}</Text> :
                null}
        </Box>
        {isType ?
            <Text color="textSecondary" fontSize="12px">{node.count}</Text> :
            <EntryActions node={node} navigate={navigate} />}
    </Flex>;
};

function EntryActions({node, navigate}: {node: StackResourceEntryNode; navigate: ReturnType<typeof useNavigate>}): React.ReactNode {
    return <Flex gap="4px" alignItems="center">
        <IconButton
            compact
            tooltip="Open in a new tab"
            icon="heroArrowUpRight"
            onClick={() => openEntry(node)}
        />
        <IconButton
            compact
            tooltip="Go to resource"
            icon="heroArrowRight"
            onClick={() => {
                dialogStore.success();
                navigate(AppRoutes.prefix + node.href);
            }}
        />
    </Flex>;
}

function openEntry(node: StackResourceEntryNode): void {
    if (node.externalHref) {
        window.open(node.externalHref, "_blank", "noopener,noreferrer");
    } else {
        window.open(AppRoutes.prefix + node.href, "_blank", "noopener,noreferrer");
    }
}

const StackResourcesDialogClass = injectStyle("stack-resources-dialog", k => `
    ${k} .tree-container {
        margin-top: 12px;
        height: 400px;
    }
`);
