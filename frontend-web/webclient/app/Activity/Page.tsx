import * as React from "react";
import { connect } from "react-redux";
import { ActivityProps, ActivityDispatchProps, ActivityFilter } from "Activity";
import * as Module from "Activity";
import { ActivityReduxObject, ReduxObject } from "DefaultObjects";
import { resetActivity, fetchActivity, setLoading, updateActivityFilter } from "./Redux/ActivityActions";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { Dispatch } from "redux";
import { Box, Input, Label, InputGroup } from "ui-components";
import { MainContainer } from "MainContainer/MainContainer";
import { SidebarPages } from "ui-components/Sidebar";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import * as Scroll from "Scroll";
import { DatePicker } from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import BaseLink from "ui-components/BaseLink";
import { ActivityFeedGrouped } from "./Feed";

const pageSize = 250;

class Activity extends React.Component<ActivityProps> {
    public componentDidMount() {
        this.props.onMount();
        this.props.fetchActivity(null, pageSize);
        this.props.setRefresh(() => {
            this.props.resetActivity();
            this.props.fetchActivity(null, pageSize);
        });
    }

    public componentWillUnmount() {
        this.props.setRefresh(undefined);
    }

    render() {
        return (
            <MainContainer
                main={this.renderMain()}
                header={this.renderHeader()}
                sidebar={this.renderSidebar()}
                sidebarSize={340}
            />
        );
    }

    private renderHeader(): React.ReactNode {
        return <Heading.h2>File Activity</Heading.h2>;
    }

    private renderMain(): React.ReactNode {
        const { scroll, error, loading, fetchActivity } = this.props;
        return <React.StrictMode>
            <Scroll.List
                scroll={scroll}
                scrollSize={pageSize}
                onNextScrollRequested={req => fetchActivity(req.offset, req.scrollSize)}
                loading={loading}
                errorMessage={error}
                renderer={scroll => (
                    <ActivityFeedGrouped activity={scroll.items} />
                )}
            />

        </React.StrictMode>;
    }

    private renderSidebar(): React.ReactNode {
        const { updateFilter, minTimestamp, maxTimestamp } = this.props;
        return (
            <>
                {this.renderQuickFilters()}
                <Heading.h3>Active Filters</Heading.h3>
                <form onSubmit={e => e.preventDefault()}>
                    <Label>A Label</Label>
                    <Input />

                    <TimeFilter
                        text={"Event created after"}
                        selected={minTimestamp}
                        onChange={minTimestamp => updateFilter({ minTimestamp })} />

                    <TimeFilter
                        text={"Event created before"}
                        selected={maxTimestamp}
                        onChange={maxTimestamp => updateFilter({ maxTimestamp })} />

                </form>
            </>
        );
    }

    private renderQuickFilters(): React.ReactNode {
        const now = new Date();
        const startOfToday = getStartOfDay(now);
        const startOfWeek = getStartOfWeek(now);
        const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - 1));
        const startOfPreviousWeek = getStartOfWeek(new Date(startOfWeek.getTime() - 1));

        return <>
            <Heading.h3>Quick Filters</Heading.h3>
            <Box mb={16}>
                {this.filter("Today", { minTimestamp: startOfToday, maxTimestamp: undefined })}
                {this.filter("Yesterday", { maxTimestamp: startOfToday, minTimestamp: startOfYesterday })}
                {this.filter("This week", { minTimestamp: startOfWeek, maxTimestamp: undefined })}
                {this.filter("Last week", { minTimestamp: startOfPreviousWeek, maxTimestamp: startOfWeek })}
            </Box>
        </>;
    }

    private filter(title: string, filter: Partial<ActivityFilter>) {
        return <BaseLink
            style={{ display: "block" }}
            href={"javascript:void(0)"}
            onClick={e => {
                e.preventDefault();
                this.props.updateFilter(filter)
            }}>{title}</BaseLink>
    }
}

export const getStartOfDay = (d: Date): Date => {
    const copy = new Date(d);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
}

export const getStartOfWeek = (d: Date): Date => {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1)

    const copy = new Date(d);
    copy.setDate(diff);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
}

export const TimeFilter = (props: { text: string, onChange: (ts?: Date) => void, selected?: Date }) => (
    <>
        <Label>{props.text}</Label>
        <InputGroup>
            <DatePicker
                showTimeSelect
                placeholderText={"Don't filter"}
                selected={props.selected}
                onChange={ts => props.onChange(ts || undefined)}
                timeIntervals={15}
                isClearable
                selectsStart
                timeFormat="HH:mm"
                dateFormat="dd/MM/yy HH:mm"
            />
        </InputGroup>
    </>
);

const mapStateToProps = ({ activity }: ReduxObject): ActivityReduxObject & Module.ActivityOwnProps => ({
    ...activity
});

const mapDispatchToProps = (dispatch: Dispatch): ActivityDispatchProps => ({
    fetchActivity: async (offset, pageSize) => {
        dispatch(setLoading(true));
        dispatch(await fetchActivity(offset, pageSize));
    },

    onMount: () => {
        dispatch(updatePageTitle("Activity"))
        dispatch(setActivePage(SidebarPages.Activity))
    },

    resetActivity: () => {
        dispatch(resetActivity());
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    updateFilter: (filter) => dispatch(updateActivityFilter(filter)),
});

export default connect(mapStateToProps, mapDispatchToProps)(Activity);