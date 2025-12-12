import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {Button, Divider, Icon, Input, Label, MainContainer, Select, TextArea} from "@/ui-components";
import * as Api from "./api";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {stopPropagation, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {PeriodSelectorBodyStyle, PeriodStyle} from "@/Accounting/Usage";
import {addDays, addMonths} from "date-fns";
import {injectStyle} from "@/Unstyled";

function Add() {

    const [options] = useCloudAPI(Api.retrieveOptions(), {byProvider: {}});
    const [date, setDate] = React.useState(new Date());

    const permissions = options.data.byProvider;
    const permissionKeys = Object.keys(permissions);
    const serviceProviders = permissionKeys;

    return <MainContainer main={
        <div>
            <div>
                <Label>
                    Title
                    <Input placeholder="Title..." />
                </Label>
                <Label>
                    Description
                    <TextArea placeholder="Description..." />
                </Label>
            </div>
            <div>
                <Label>
                    Service provider (widget thingy)
                    <Select>

                    </Select>
                </Label>
            </div>
            <div>
                Expiration
                <div>
                    <PeriodSelector date={date} onChange={setDate} />
                </div>
            </div>
            <div>
                Token permissions
                {permissionKeys.flatMap(key =>
                    permissions[key].availablePermissions.map(it => <div>
                        {it.name}
                    </div>)
                )}
            </div>
        </div>
    } />

}

function PeriodSelector(props: {date: Date; onChange(d: Date): void}): React.ReactNode {

    function formatTs(ts: number): string {
        const d = new Date(ts);
        return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
    }

    const onRelativeUpdated = React.useCallback((ev: React.SyntheticEvent) => {
        let today = new Date();
        today.setHours(0);
        today.setMinutes(0);
        today.setSeconds(0);

        const t = ev.target as HTMLElement;
        const distance = parseInt(t.getAttribute("data-unit") ?? "0", 10);
        const unit = t.getAttribute("data-relative-unit") as "month" | "day";

        switch (unit) {
            case "day":
                today = addDays(today, distance);
                break;
            case "month":
                today = addMonths(today, distance);
                break;
        }

        props.onChange(today);
    }, []);

    const onChange = React.useCallback((ev: React.SyntheticEvent) => {
        const target = ev.target as HTMLInputElement;
        if (!target) return;
        const date = target.valueAsDate;
        if (!date) return;
        props.onChange(date);
    }, []);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent
        noYPadding={true}
        onOpeningTriggerClick={() => void 0}
        trigger={
            <div className={PeriodStyle}>
                <div style={{width: "182px"}}>{formatTs(props.date.getTime())}</div>
                <Icon name="heroChevronDown" size="14px" ml="4px" mt="4px" />
            </div>
        }
    >
        <div className={DateSelector}>
            <div>
                <b>Specific date</b>
                <Input pl="8px" pr="8px" className={"start"} onChange={onChange} type={"date"} value={formatTs(props.date.getTime())} />
            </div>
            <Divider />
            <div>
                <b>Relative date</b>

                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"7"}>7 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"30"}>30 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"90"}>90 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"6"}>6 months from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"12"}>12 months from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"36"}>3 years from today
                </div>
            </div>
        </div>
    </ClickableDropdown>;
};

const DateSelector = injectStyle("date-selector", cl => `
    ${cl} {
        margin-top: 8px;
        width: 350px;
    }

    ${cl} input {
        margin-left: 8px;
        margin-right: 8px;
        width: calc(100% - 16px);
    }

    ${cl} b {
        padding-left: 8px;
    }

    ${cl} > div:nth-child(3) {
        padding-top: 0px;
        gap: 8px;
        display: grid;
    }

    ${cl} > div:nth-child(3) > div {
        flex-grow: 1;
        display: flex;
        flex-direction: column;
        padding: 8px;
        padding-left: 16px;
    }

    ${cl} > div:nth-child(3) > div:hover {
        cursor: pointer;
        background-color: var(--rowHover);
    }
`);

export default Add;