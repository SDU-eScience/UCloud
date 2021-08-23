import * as React from "react";
import {BulkRequest, provider} from "UCloud";
import ResourceDoc = provider.ResourceDoc;
import {IconName} from "ui-components/Icon";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import {ResourcePermissionEditor} from "ui-components/ResourcePermissionEditor";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {dateToString} from "Utilities/DateUtilities";
import {prettierString, shortUUID} from "UtilityFunctions";
import {TextSpan} from "ui-components/Text";
import {currencyFormatter} from "Project/Resources";
import {Section} from "ui-components/Section";
import Grid from "ui-components/Grid";

interface ResourcePageProps<T extends ResourceDoc> {
    entityName: string;
    aclOptions: { icon: IconName; name: string, title?: string }[];
    entity: T;
    reload: () => void;
    stats?: ResourceStat<T>[];
    updateAclEndpoint: (request: BulkRequest<{ id: string; acl: unknown[] }>) => APICallParameters;

    beforeStats?: React.ReactElement;
    beforePermissions?: React.ReactElement;
    beforeUpdates?: React.ReactElement;
    beforeEnd?: React.ReactElement;

    showId?: boolean;
    showState?: boolean;
    showProduct?: boolean;
    showBilling?: boolean;
    showMissingPermissionHelp?: boolean;
}

interface ResourceStat<T extends ResourceDoc> {
    property?: keyof T;
    render?: (entity: T) => React.ReactChild;
    title?: string;
    inline?: boolean;
}

export function ResourcePage<T extends ResourceDoc>(props: ResourcePageProps<T>): React.ReactElement | null {
    const filteredUpdates = props.entity.updates.filter((update) => !(update["state"] === null && !update.status));
    return <>
        {props.beforeStats}
        <Grid width={"500px"} margin={"0 auto"} gridGap={"32px"}>
            <Section>
                {!(props.showId ?? true) ? null :
                    <Flex>
                        <Heading.h4 flexGrow={1}>ID</Heading.h4>
                        {shortUUID(props.entity.id)}
                    </Flex>
                }

                {!(props.showState ?? true) || !props.entity.status["state"] ? null :
                    <Flex>
                        <Heading.h4 flexGrow={1}>State</Heading.h4>
                        {prettierString(props.entity.status["state"])}
                    </Flex>
                }

                {props.stats?.map(stat =>
                    <React.Fragment key={stat.property?.toString() ?? stat.title ?? "unknown"}>
                        <Flex>
                            <Heading.h4 flexGrow={1}>{stat.title}</Heading.h4>
                            {!(stat.inline ?? true) ? null :
                                stat.render?.(props.entity) ?? (stat.property ? props.entity[stat.property] : null)}
                        </Flex>
                        {(stat.inline ?? true) ? null :
                            stat.render?.(props.entity) ?? (stat.property ? props.entity[stat.property] : null)}
                    </React.Fragment>
                )}

                {props.showProduct !== true ? null :
                    <Flex>
                        <Heading.h4 flexGrow={1}>Product</Heading.h4>
                        {props.entity.specification.product?.provider} / {props.entity.specification.product?.id}
                    </Flex>
                }

                {props.showBilling !== true ? null :
                    <Flex>
                        <Heading.h4 flexGrow={1}>Balance charged</Heading.h4>
                        {currencyFormatter(props.entity.billing.creditsCharged)}
                    </Flex>
                }
            </Section>

            {props.beforePermissions}

            {props.entity.owner.project === undefined ? null : (
                <Section>
                    <Heading.h4 mb={8}>Permissions</Heading.h4>
                    <ResourcePermissionEditor entityName={props.entityName} options={props.aclOptions}
                                              entity={props.entity} reload={props.reload}
                                              showMissingPermissionHelp={props.showMissingPermissionHelp}
                                              updateAclEndpoint={props.updateAclEndpoint}/>
                </Section>
            )}

            {props.beforeUpdates}

            {filteredUpdates.length === 0 ? null : <>
                <Section>
                    <Heading.h4>Updates</Heading.h4>
                    <Table>
                        <tbody>
                        {filteredUpdates.map((update, idx) => {
                            return <TableRow key={idx}>
                                <TableCell>{dateToString(update.timestamp)}</TableCell>
                                <TableCell>
                                    {!update["state"] ? null : prettierString(update["state"])}
                                </TableCell>
                                <TableCell>
                                    {update.status ? <TextSpan mr={"10px"}>{update.status}</TextSpan> : null}
                                </TableCell>
                            </TableRow>
                        })}
                        </tbody>
                    </Table>
                </Section>
            </>}

            {props.beforeEnd}
        </Grid>
    </>;
}
