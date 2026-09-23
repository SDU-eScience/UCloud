import * as React from "react";
import {useEffect, useState} from "react";
import {callAPI} from "@/Authentication/DataHook";
import type {PrivateNetwork} from "@/UCloud/PrivateNetworkApi";
import PrivateNetworkIpApi, {PrivateNetworkIp} from "@/UCloud/PrivateNetworkIpApi";
import {retrieveSupportV2} from "@/UCloud/ResourceApi";
import {fetchAll} from "@/Utilities/PageUtilities";
import {bulkRequestOf, displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {Box, Button, Flex, Icon, Input, Label} from "@/ui-components";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {ProductV2} from "@/Accounting";
import {dateToString} from "@/Utilities/DateUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";

export async function retrieveNetworkReservations(networkId: string): Promise<PrivateNetworkIp[]> {
    const reservations = await fetchAll(next => callAPI(PrivateNetworkIpApi.browse({itemsPerPage: 250, next, includeOthers: true})));
    return reservations.filter(it => it.specification.network === networkId);
}

const IpList = injectStyle("private-network-ip-list", k => `
    ${k} > div {
        padding: 12px 0;
    }
    ${k} > div:not(:last-child) {
        border-bottom: 1px solid var(--borderColor);
    }
`);

export function PrivateNetworkReservations({resource, onCountChange, reloadKey}: {
    resource: PrivateNetwork;
    onCountChange: (count: number) => void;
    reloadKey: PrivateNetwork;
}): React.ReactNode {
    const [reservations, setReservations] = useState<PrivateNetworkIp[]>([]);
    const [products, setProducts] = useState<ProductV2[]>([]);
    const [selectedProduct, setSelectedProduct] = useState("");
    const [requestedIp, setRequestedIp] = useState("");
    const [busy, setBusy] = useState(false);
    const [adding, setAdding] = useState(false);

    const refresh = () => retrieveNetworkReservations(resource.id).then(items => {
        setReservations(items);
        onCountChange(items.length);
    })
        .catch(e => displayErrorMessageOrDefault(e, "Failed to load private IP reservations"));

    useEffect(() => {
        refresh();
        retrieveSupportV2(PrivateNetworkIpApi).then(result => {
            setProducts(result.newProducts.filter(p => p.category.provider === resource.specification.product.provider));
        }).catch(e => displayErrorMessageOrDefault(e, "Failed to load private IP products"));
    }, [resource.id, reloadKey]);

    const matchingProducts = products;
    const product = matchingProducts.find(p => `${p.category.name}/${p.name}` === selectedProduct) ?? matchingProducts[0];
    const ip = requestedIp.trim();
    const validIpv4 = ip === "" || (/^(?:0|[1-9]\d{0,2})(?:\.(?:0|[1-9]\d{0,2})){3}$/.test(ip) &&
        ip.split(".").every(part => Number(part) <= 255));
    const cidr = resource.status.cidrBlock?.split("/");
    const prefixLength = Number(cidr?.[1]);
    const prefixOctetCount = prefixLength === 16 ? 2 : prefixLength === 24 ? 3 : 0;
    const networkPrefix = prefixOctetCount > 0 ?
        `${cidr?.[0].split(".").slice(0, prefixOctetCount).join(".")}.` : "";
    const ipv4InputPattern = prefixOctetCount === 0 ?
        String.raw`(?:[0-9]{1,3}\.){0,3}[0-9]{0,3}` :
        String.raw`(?:[0-9]{1,3}\.){${prefixOctetCount},3}[0-9]{0,3}`;
    const inNetworkRange = ip === "" || prefixOctetCount === 0 || ip.split(".").slice(0, prefixOctetCount)
        .every((octet, index) => octet === cidr?.[0].split(".")[index]);
    const validIp = validIpv4 && inNetworkRange;
    const isPartialIp = ip !== "" && new RegExp(`^(?:${ipv4InputPattern})$`).test(ip) &&
        (ip.endsWith(".") || ip.split(".").length < 4);
    const showIpError = !validIp && !isPartialIp;
    const ipError = !validIpv4 ? "Enter a valid IPv4 address." : "Enter an address within the network range.";

    const create = async () => {
        if (!product || !validIp) return;
        setBusy(true);
        try {
            await callAPI(PrivateNetworkIpApi.create(bulkRequestOf({
                network: resource.id,
                product: {id: product.name, category: product.category.name, provider: product.category.provider},
                ...(ip ? {ipAddress: ip} : {}),
            })));
            setRequestedIp("");
            setAdding(false);
            await refresh();
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to reserve private IP");
        } finally {
            setBusy(false);
        }
    };

    const remove = async (reservation: PrivateNetworkIp) => {
        setBusy(true);
        try {
            await callAPI(PrivateNetworkIpApi.remove(bulkRequestOf({id: reservation.id})));
            await refresh();
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to delete private IP reservation");
        } finally {
            setBusy(false);
        }
    };

    const toggleAdding = () => {
        if (adding) {
            setAdding(false);
            return;
        }
        if (requestedIp === "" && networkPrefix) setRequestedIp(networkPrefix);
        setAdding(true);
    };

    return <TabbedCard style={{minHeight: "340px"}} rightControls={!resource.status.cidrBlock ? undefined :
        <Button disabled={busy} onClick={toggleAdding}>
            <Icon name={adding ? "heroMinus" : "heroPlus"} mr="8px" />
            {adding ? "Cancel" : "Reserve IP"}
        </Button>}>
        <TabbedCardTab icon="heroWifi" name="Reserved IPs">
            <div className={IpList}>
                {reservations.length === 0 ? <Box color="textSecondary" py="12px">No IP addresses reserved yet.</Box> :
                    reservations.map(reservation =>
                        <Flex key={reservation.id} alignItems="center" gap="16px" flexWrap="wrap">
                            <Icon name="heroWifi" size={20} />
                            <Box flexGrow={1}>
                                <b>{reservation.status.ipAddress ?? reservation.specification.ipAddress ?? "Allocating address..."}</b>
                                <Box color="textSecondary" fontSize="12px">
                                    {reservation.specification.ipAddress ? "Requested address" : "Automatically allocated"}
                                    {" · "}{dateToString(reservation.createdAt)}
                                </Box>
                            </Box>
                            <TooltipV2 tooltip="Hold to delete reservation">
                                <ConfirmationButton icon="heroTrash" color="errorMain" disabled={busy}
                                    onAction={async () => { await remove(reservation); }} />
                            </TooltipV2>
                        </Flex>)}
            </div>
            {adding ? <Flex gap="12px" flexWrap="wrap" alignItems="start" mt="20px">
                {matchingProducts.length > 1 ? <Box>
                    <Label>Product</Label>
                    <select value={product ? `${product.category.name}/${product.name}` : ""} onChange={e => setSelectedProduct(e.target.value)}>
                        {matchingProducts.map(p => <option key={`${p.category.name}:${p.name}`} value={`${p.category.name}/${p.name}`}>{p.category.name} / {p.name}</option>)}
                    </select>
                </Box> : null}
                <Box flexGrow={1} minWidth="240px">
                    <Label>IPv4 address (optional)</Label>
                    <Input autoFocus value={requestedIp} onChange={e => setRequestedIp(e.target.value)}
                        placeholder={resource.status.cidrBlock ?? "Automatic allocation"} pattern={ipv4InputPattern}
                        error={showIpError}
                        onKeyDown={event => {
                            if (event.key === "Enter") {
                                event.preventDefault();
                                if (!busy && product && validIp) void create();
                            } else if (event.key === "Escape") {
                                event.preventDefault();
                                event.stopPropagation();
                                setAdding(false);
                            }
                        }} />
                    {showIpError ? <Box color="errorMain" mt="4px">{ipError}</Box> : null}
                </Box>
                <Button mt="24px" disabled={busy || !product || !validIp} onClick={create}>Reserve</Button>
            </Flex> : null}
            {!resource.status.cidrBlock ? <Box mt="16px" color="textSecondary">
                Reservations are available once the network has an address range.
            </Box> : null}
        </TabbedCardTab>
    </TabbedCard>;
}
