import {ActivityDispatchProps, ActivityFilter, ActivityProps} from "Activity";
import * as Module from "Activity";
import {ActivityReduxObject, ReduxObject} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import * as Scroll from "Scroll";
import {Box, InputGroup, Label} from "ui-components";
import BaseLink from "ui-components/BaseLink";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {ActivityFeedFrame, ActivityFeedItem, ActivityFeedSpacer} from "./Feed";
import {fetchActivity, resetActivity, setLoading, updateActivityFilter} from "./Redux/ActivityActions";
import Input from "ui-components/Input";
import {Client} from "Authentication/HttpClientInstance";

const scrollSize = 250;

const dropdownOptions: Array<{text: string; value: string}> = [
    {value: "NO_FILTER", text: "Don't filter"},
    {value: Module.ActivityType.DELETED, text: "Deletions"},
    {value: Module.ActivityType.DOWNLOAD, text: "Downloads"},
    {value: Module.ActivityType.FAVORITE, text: "Favorites"},
    {value: Module.ActivityType.MOVED, text: "Moves"},
    {value: Module.ActivityType.COPIED, text: "Copies"},
    {value: Module.ActivityType.ALLUSEDINAPP, text: "Files Used By App"},
    {value: Module.ActivityType.DIRECTORYCREATED, text: "Directory Creation"},
    {value: Module.ActivityType.RECLASSIFIED, text: "Reclassifications"},
    {value: Module.ActivityType.UPLOADED, text: "Uploads"},
    {value: Module.ActivityType.UPDATEDACL, text: "ACL Updates"},
    {value: Module.ActivityType.SHAREDWITH, text: "Shares"}
];

function Activity(props: ActivityProps): JSX.Element {

    React.useEffect(() => {
        props.onMount();
        props.resetActivity();
        props.fetchActivity({scrollSize});
        props.setRefresh(() => {
            props.resetActivity();
            props.fetchActivity({scrollSize}, props);
        });
        return () => props.setRefresh();
    }, []);

    function renderHeader(): React.ReactNode {
        return <Heading.h2>File Activity</Heading.h2>;
    }

    function renderMain(): React.ReactNode {
        const {scroll, loading, fetchActivity} = props;
        return (
            <Scroll.List
                scroll={scroll}
                scrollSize={scrollSize}
                onNextScrollRequested={req => fetchActivity(req, props)}
                loading={loading}
                frame={(ref, children) => <ActivityFeedFrame containerRef={ref}>{children}</ActivityFeedFrame>}
                renderer={props => <ActivityFeedItem activity={props.item} />}
                spacer={height => <ActivityFeedSpacer key={`spacer${height}`} height={height} />}
            />
        );
    }

    function renderSidebar(): React.ReactNode {
        const {minTimestamp, maxTimestamp, type} = props;

        const ref = React.useRef<number>(-1);

        const onKeyUp = React.useCallback((e) => {
            const user = e.target?.value ?? "";
            if (ref.current !== -1) {
                window.clearTimeout(ref.current);
            }

            ref.current = (window.setTimeout(() => {
                applyFilter({user});
            }, 500));

        }, []);

        return (
            <>
                {renderQuickFilters()}
                <Heading.h3>Active Filters</Heading.h3>
                <Box mb={16}>
                    <Label>Filter by event type</Label>
                    <ClickableDropdown
                        chevron
                        options={dropdownOptions}
                        trigger={type === undefined ? "Don't filter" : dropdownOptions.find(i => i.value === type)!.text}
                        onChange={e => applyFilter({type: e === "NO_FILTER" ? undefined : e as Module.ActivityType})}
                    />
                </Box>

                {!Client.hasActiveProject ? null :
                    <Box mb={16}>
                        <Label>Filter by username</Label>
                        <InputGroup>
                            <Input
                                placeholder={"Don't filter"}
                                onKeyUp={onKeyUp}
                            />
                        </InputGroup>
                    </Box>
                }

                <TimeFilter
                    text={"Event created after"}
                    selected={minTimestamp}
                    onChange={minTimestamp => applyFilter({minTimestamp})}
                />

                <TimeFilter
                    text={"Event created before"}
                    selected={maxTimestamp}
                    onChange={maxTimestamp => applyFilter({maxTimestamp})}
                />
            </>
        );
    }

    function renderQuickFilters(): React.ReactNode {
        const now = new Date();
        const startOfToday = getStartOfDay(now);
        const startOfWeek = getStartOfWeek(now);
        const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - 1));

        return (
            <Box mb={16}>
                <Heading.h3>Quick Filters</Heading.h3>
                <Box mb={16}>
                    {filter("Today", {minTimestamp: startOfToday, maxTimestamp: undefined})}
                    {filter("Yesterday", {maxTimestamp: startOfToday, minTimestamp: startOfYesterday})}
                    {filter("This week", {minTimestamp: startOfWeek, maxTimestamp: undefined})}
                    {filter("No filter", {minTimestamp: undefined, maxTimestamp: undefined, type: undefined})}
                </Box>
            </Box>
        );
    }

    function filter(title: string, filter: Partial<ActivityFilter>): JSX.Element {
        return (
            <BaseLink
                style={{display: "block"}}
                href="#"
                onClick={e => {
                    e.preventDefault();
                    applyFilter(filter);
                }}
            >
                {title}
            </BaseLink>
        );
    }

    function applyFilter(filter: Partial<ActivityFilter>): void {
        props.updateFilter(filter);
        props.resetActivity();
        props.fetchActivity({scrollSize}, {...props, ...filter});
    }

    return (
        <MainContainer
            main={renderMain()}
            header={renderHeader()}
            sidebar={renderSidebar()}
            sidebarSize={340}
        />
    );
}

export const getStartOfDay = (d: Date): Date => {
    const copy = new Date(d);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
};

export const getStartOfWeek = (d: Date): Date => {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);

    const copy = new Date(d);
    copy.setDate(diff);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
};

export const TimeFilter = (props: {text: string; onChange: (ts?: Date) => void; selected?: Date}): JSX.Element => (
    <Box mb={16}>
        <Label>{props.text}</Label>
        <InputGroup>
            <DatePicker
                showTimeInput
                placeholderText={"Don't filter"}
                selected={props.selected}
                onChange={ts => props.onChange(ts ?? undefined)}
                timeIntervals={15}
                isClearable
                selectsStart
                timeFormat="HH:mm"
                dateFormat="dd/MM/yy HH:mm"
            />
        </InputGroup>
    </Box>
);

const mapStateToProps = ({activity}: ReduxObject): ActivityReduxObject & Module.ActivityOwnProps => activity;

const mapDispatchToProps = (dispatch: Dispatch): ActivityDispatchProps => ({
    fetchActivity: async (req, filter) => {
        dispatch(setLoading(true));
        dispatch(await fetchActivity(req, filter));
    },

    onMount: () => {
        dispatch(updatePageTitle("Activity"));
        dispatch(setActivePage(SidebarPages.Activity));
    },

    resetActivity: () => {
        dispatch(resetActivity());
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    updateFilter: (filter) => {
        dispatch(updateActivityFilter(filter));
    },
});

export default connect(mapStateToProps, mapDispatchToProps)(Activity);
