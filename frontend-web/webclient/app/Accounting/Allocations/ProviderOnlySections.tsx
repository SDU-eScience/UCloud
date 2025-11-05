import * as React from "react";
import {State, UIEvent} from "@/Accounting/Allocations/State";
import {Accordion, Box, Button, DataList, Flex, Icon, Input, Label, Select, TextArea} from "@/ui-components";
import {bulkRequestOf, extractErrorMessage, stopPropagation} from "@/UtilityFunctions";
import {Tree, TreeNode} from "@/ui-components/Tree";
import * as Accounting from "@/Accounting";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {MandatoryField} from "@/UtilityComponents";
import WAYF from "@/Grants/wayf-idps.json";
import {injectStyle} from "@/Unstyled";
import {useCallback, useRef} from "react";
import {removePrefixFrom} from "@/Utilities/TextUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {callAPI} from "@/Authentication/DataHook";
import * as Gifts from "@/Accounting/Gifts";
import {Client} from "@/Authentication/HttpClientInstance";

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

const giftClass = injectStyle("gift", k => `
    ${k} th, ${k} td {
        padding-bottom: 10px;
    }

    ${k} th {
        vertical-align: top;
        text-align: left;
        padding-right: 20px;
    }
    
    ${k} ul {
        margin: 0;
        padding: 0;
        padding-left: 1em;
    }
`);

export const ProviderOnlySections: React.FunctionComponent<{
    state: State,
    dispatchEvent: (event: UIEvent) => unknown,
}> = ({state, dispatchEvent}) => {
    const onRootAllocationInput = useCallback((ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const elem = ev.target as (HTMLInputElement | HTMLSelectElement);
        const name = elem.getAttribute("name");
        if (!name) return;
        const value = elem.value;

        switch (name) {
            case "root-year": {
                const year = parseInt(value);
                dispatchEvent({
                    type: "UpdateRootAllocations",
                    data: {year}
                });
                break;
            }
        }

        if (name.startsWith("root-resource-")) {
            const resourceName = removePrefixFrom("root-resource-", name);
            let amount = parseInt(value);
            if (value === "") amount = 0;
            if (!isNaN(amount)) {
                const data = {resources: {}};
                data.resources[resourceName] = amount;
                dispatchEvent({type: "UpdateRootAllocations", data: data});
            }
        }
    }, []);

    const creatingRootAllocation = useRef(false);
    const onCreateRootAllocation = useCallback(async (ev: React.SyntheticEvent) => {
        ev.preventDefault();
        if (creatingRootAllocation.current) return;
        if (!state.rootAllocations) return;

        const start = new Date();
        const end = new Date();
        {
            const year = state.rootAllocations.year;
            start.setUTCFullYear(year, 0, 1);
            start.setUTCHours(0, 0, 0, 0);

            end.setUTCFullYear(year, 11, 31);
            end.setUTCHours(23, 59, 59, 999);
        }

        try {
            const products = (state.remoteData.managedProducts ?? {});
            creatingRootAllocation.current = true;

            const requests: Accounting.RootAllocateRequestItem[] = [];
            for (const [categoryAndProvider, amount] of Object.entries(state.rootAllocations.resources)) {
                const [category, provider] = categoryAndProvider.split("/");
                const resolvedCategory = products[provider]?.find(it => it.name === category);
                if (!resolvedCategory) {
                    snackbarStore.addFailure("Internal failure while creating a root allocation. Try reloading the page!", false);
                    return;
                }

                const unit = Accounting.explainUnit(resolvedCategory);

                requests.push({
                    category: {
                        name: category,
                        provider,
                    },
                    quota: amount * unit.invBalanceFactor,
                    start: start.getTime(),
                    end: end.getTime(),
                });
            }

            await callAPI(Accounting.rootAllocate(bulkRequestOf(...requests)));
            snackbarStore.addSuccess("Root allocation has been created", false);
            dispatchEvent({type: "ResetRootAllocation"});
            dispatchEvent({type: "Init"});
        } catch (e) {
            snackbarStore.addFailure("Failed to create root allocation: " + extractErrorMessage(e), false);
            return;
        } finally {
            creatingRootAllocation.current = false;
        }
    }, [state.rootAllocations]);

    const onGiftInput = useCallback((ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const elem = ev.target as (HTMLInputElement | HTMLSelectElement);
        const name = elem.getAttribute("name");
        if (!name) return;
        const value = elem.value;
        switch (name) {
            case "gift-title": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {title: value}
                });
                break;
            }

            case "gift-description": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {description: value}
                });
                break;
            }

            case "gift-renewal": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {renewEvery: parseInt(value)}
                });
                break;
            }

            case "gift-allow-domain": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {domainAllow: value}
                });
                break;
            }
        }

        if (name.startsWith("gift-resource-")) {
            const resourceName = removePrefixFrom("gift-resource-", name);
            let amount = parseInt(value);
            if (value === "") amount = 0;
            if (!isNaN(amount)) {
                const data = {resources: {}};
                data.resources[resourceName] = amount;
                dispatchEvent({type: "UpdateGift", data: data});
            }
        }
    }, []);

    const onGiftOrgInput = useCallback((orgId: string) => {
        dispatchEvent({type: "UpdateGift", data: {orgAllow: orgId}});
    }, []);

    const creatingGift = useRef(false);
    const onCreateGift = useCallback(async (ev: React.SyntheticEvent) => {
        ev.preventDefault();
        if (!state.gifts) return;
        if (creatingGift.current) return;
        const gift: Gifts.GiftWithCriteria = {
            id: 0,
            criteria: [],
            description: state.gifts.description,
            resources: [],
            resourcesOwnedBy: Client.projectId ?? "",
            title: state.gifts.title,
            renewEvery: state.gifts.renewEvery
        };

        if (state.gifts.domainAllow) {
            const domains = state.gifts.domainAllow.split(",").map(it => it.trim());
            for (const domain of domains) {
                if (domain === "all-ucloud-users") {
                    // NOTE(Dan): undocumented because most people really should not do this
                    gift.criteria.push({type: "anyone"});
                } else {
                    gift.criteria.push({
                        type: "email",
                        domain: domain,
                    });
                }
            }
        }

        if (state.gifts.orgAllow) {
            gift.criteria.push({
                type: "wayf",
                org: state.gifts.orgAllow
            });
        }

        const products = (state.remoteData.managedProducts ?? {});
        for (const [categoryAndProvider, amount] of Object.entries(state.gifts.resources)) {
            const [category, provider] = categoryAndProvider.split("/");
            const resolvedCategory = products[provider]?.find(it => it.name === category);
            if (!resolvedCategory) {
                snackbarStore.addFailure("Internal failure while creating gift. Try reloading the page!", false);
                return;
            }
            const unit = Accounting.explainUnit(resolvedCategory);
            const actualAmount = amount * unit.invBalanceFactor;
            if (actualAmount === 0) continue;

            gift.resources.push({
                balanceRequested: actualAmount,
                category: category,
                provider: provider,
                grantGiver: "",
                period: {start: 0, end: 0},
            });
        }

        if (gift.criteria.length === 0) {
            snackbarStore.addFailure("Missing user criteria. You must define at least one!", false);
            return;
        }

        if (gift.resources.length === 0) {
            snackbarStore.addFailure("A gift must contain at least one resource!", false);
            return;
        }

        try {
            creatingGift.current = true;
            const {id} = await callAPI(Gifts.create(gift));
            gift.id = id;
            dispatchEvent({type: "GiftCreated", gift});
            snackbarStore.addSuccess("Gift Created", false);
        } catch (e) {
            snackbarStore.addFailure("Failed to create a gift: " + extractErrorMessage(e), false);
        } finally {
            creatingGift.current = false;
        }
    }, [state.gifts]);

    const onDeleteGift = useCallback(async (giftIdString?: string) => {
        if (!giftIdString) return;
        const id = parseInt(giftIdString);
        if (isNaN(id)) return;

        try {
            await callAPI(Gifts.remove({giftId: id}));
        } catch (e) {
            snackbarStore.addFailure("Failed to delete gift: " + extractErrorMessage(e), false);
            return;
        }

        dispatchEvent({type: "GiftDeleted", id});
    }, []);

    let gifts = state.remoteData.gifts ?? [];
    return <>
        {(state.remoteData.managedProviders ?? []).length > 0 && <>
            {state.rootAllocations && <>
                <h3>Root allocations</h3>
                <div>
                    Root allocations are ordinary allocations from which all other allocations are created.

                    <ul>
                        <li>You can see this because you are part of a provider project</li>
                        <li>You must create a root allocation to be able to use your provider</li>
                        <li>Once created, you can see the root allocations in the "Your allocations" panel</li>
                    </ul>
                </div>

                <Accordion title={"Create a new root allocation"}>
                    <h4>Step 1: Select a period</h4>
                    <Select
                        slim
                        value={state.rootAllocations.year}
                        onInput={onRootAllocationInput}
                        onKeyDown={stopPropagation}
                        name={"root-year"}
                    >
                        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(delta => {
                            const year = new Date().getUTCFullYear() + delta;
                            return <option key={delta} value={year.toString()}>{year}</option>;
                        })}
                    </Select>

                    <h4>Step 2: Select allocation size</h4>
                    <Tree>
                        {Object.entries((state.remoteData.managedProducts ?? {})).map(([providerId, page]) =>
                            <React.Fragment
                                key={providerId}>
                                {page.map(cat => <TreeNode
                                    key={cat.name + cat.provider}
                                    left={<Flex gap={"4px"}>
                                        <Icon name={Accounting.productTypeToIcon(cat.productType)} size={20}/>
                                        <code>{cat.name} / {cat.provider}</code>
                                    </Flex>}
                                    right={<Flex gap={"4px"}>
                                        <Input
                                            height={20}
                                            placeholder={"0"}
                                            name={`root-resource-${cat.name}/${cat.provider}`}
                                            value={state.rootAllocations?.resources?.[`${cat.name}/${cat.provider}`] ?? ""}
                                            onInput={onRootAllocationInput}
                                            onKeyDown={stopPropagation}
                                        />
                                        <Box width={"150px"}>{Accounting.explainUnit(cat).name}</Box>
                                    </Flex>}
                                />)}
                            </React.Fragment>)}
                    </Tree>

                    <Button my={16} onClick={onCreateRootAllocation}>Create root allocations</Button>
                </Accordion>

                <Box mt={32}/>
            </>}

            {state.gifts && <>
                <h3>Gifts</h3>
                <div>
                    Gifts are free resources which are automatically claimed by active UCloud users fulfilling
                    certain
                    criteria.
                    <ul>
                        <li>As a provider, you can see your gifts and define new gifts</li>
                        <li>You can delete gifts, but this will not retract the gifts that have already been claimed
                        </li>
                    </ul>
                </div>

                <Accordion title={`View existing gifts (${gifts.length})`}>
                    {gifts.length === 0 ? <>This project currently has no active gifts!</> : <Tree>
                        {gifts.map(g =>
                            <TreeNode
                                key={g.id}
                                left={g.title}
                            >
                                <table className={giftClass}>
                                    <tbody>
                                    <tr>
                                        <th>Description</th>
                                        <td>{g.description}</td>
                                    </tr>
                                    <tr>
                                        <th>Criteria</th>
                                        <td>
                                            <ul>
                                                {g.criteria.map(c => {
                                                    switch (c.type) {
                                                        case "anyone":
                                                            return <li key={c.type}>All UCloud users</li>
                                                        case "wayf":
                                                            return <li key={c.org + "wayf"}>Users
                                                                from <i>{c.org}</i></li>
                                                        case "email":
                                                            return <li key={c.domain + "email"}>@{c.domain}</li>
                                                    }
                                                })}
                                            </ul>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th>Resources</th>
                                        <td>
                                            <ul>
                                                {g.resources.map((r, idx) => {
                                                    const pc = (state.remoteData.managedProducts ?? {})[r.provider]?.find(it => it.name === r.category);
                                                    if (!pc) return null;
                                                    return <li key={idx}>
                                                        {r.category} / {r.provider}: {Accounting.balanceToString(pc, r.balanceRequested)}
                                                    </li>
                                                })}
                                            </ul>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th>Granted</th>
                                        <td>
                                            {g.renewEvery == 0 ? "Once" : (g.renewEvery == 1 ? "Every month" : "Every " + g.renewEvery.toString() + " months")}
                                        </td>
                                    </tr>
                                    <tr>
                                        <th>Delete</th>
                                        <td>
                                            <ConfirmationButton
                                                actionText={"Delete"}
                                                icon={"heroTrash"}
                                                onAction={onDeleteGift}
                                                actionKey={g.id.toString()}
                                            />
                                        </td>
                                    </tr>
                                    </tbody>
                                </table>
                            </TreeNode>
                        )}
                    </Tree>}
                </Accordion>

                <Accordion title={"Create a gift"}>
                    <form onSubmit={onCreateGift}>
                        <Flex gap={"8px"} flexDirection={"column"}>
                            <Label>
                                Title <MandatoryField/>
                                <Input
                                    name={"gift-title"}
                                    value={state.gifts.title}
                                    onInput={onGiftInput}
                                    onKeyDown={stopPropagation}
                                    placeholder={"For example: Gift for employees at SDU"}
                                />
                            </Label>
                            <Label>
                                Description:

                                <TextArea
                                    name={"gift-description"}
                                    value={state.gifts.description}
                                    rows={3}
                                    onInput={onGiftInput}
                                    onKeyDown={stopPropagation}
                                />
                            </Label>
                            <Label>
                                Is this gift periodically renewed or a one-time grant? <MandatoryField/>
                                <Select
                                    name={"gift-renewal"}
                                    slim
                                    value={state.gifts.renewEvery}
                                    onInput={onGiftInput}
                                    onKeyDown={stopPropagation}
                                >
                                    <option value={"0"}>One-time per-user grant</option>
                                    <option value={"1"}>Renew every month</option>
                                    <option value={"6"}>Renew every 6 months</option>
                                    <option value={"12"}>Renew every 12 months</option>
                                </Select>
                            </Label>
                            <Label>
                                Allow if user belongs to this organization
                                <DataList
                                    options={wayfIdpsPairs}
                                    onSelect={onGiftOrgInput}
                                    placeholder={"Type to search..."}
                                />
                            </Label>
                            <Label>
                                Allow if email domain matches any of the following (comma-separated)
                                <Input
                                    name={"gift-allow-domain"}
                                    placeholder={"For example: sdu.dk, cloud.sdu.dk"}
                                    onInput={onGiftInput}
                                    value={state.gifts.domainAllow}
                                    onKeyDown={stopPropagation}
                                />
                            </Label>

                            <Label>Resources</Label>
                            <Tree>
                                {Object.entries((state.remoteData.managedProducts ?? {})).map(([providerId, page]) =>
                                    <React.Fragment
                                        key={providerId}>
                                        {page.map(cat => <TreeNode
                                            key={cat.name + cat.provider}
                                            left={<Flex gap={"4px"}>
                                                <Icon name={Accounting.productTypeToIcon(cat.productType)}
                                                      size={20}/>
                                                <code>{cat.name} / {cat.provider}</code>
                                            </Flex>}
                                            right={<Flex gap={"4px"}>
                                                <Input
                                                    height={20}
                                                    placeholder={"0"}
                                                    name={`gift-resource-${cat.name}/${cat.provider}`}
                                                    onInput={onGiftInput}
                                                    value={state.gifts?.resources?.[`${cat.name}/${cat.provider}`] ?? ""}
                                                    onKeyDown={stopPropagation}
                                                />
                                                <Box width={"150px"}>{Accounting.explainUnit(cat).name}</Box>
                                            </Flex>}
                                        />)}
                                    </React.Fragment>)}
                            </Tree>
                        </Flex>
                        <Button type={"submit"}>
                            Create gift
                        </Button>
                    </form>
                </Accordion>

                <Box mt={32}/>
            </>}
        </>}
    </>;
}
