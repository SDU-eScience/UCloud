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
import { ActivityFeedFrame, ActivityFeedItem } from "./Feed";

const scrollSize = 250;

class Activity extends React.Component<ActivityProps> {
    public componentDidMount() {
        this.props.onMount();
        this.props.fetchActivity({ scrollSize });
        this.props.setRefresh(() => {
            this.props.resetActivity();
            this.props.fetchActivity({ scrollSize }, this.props);
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
                scrollSize={scrollSize}
                onNextScrollRequested={req => fetchActivity(req, this.props)}
                loading={loading}
                errorMessage={error}
                frame={(ref, children) => (
                    <ActivityFeedFrame containerRef={ref}>{children}</ActivityFeedFrame>
                )}
                renderer={item => (<ActivityFeedItem activity={item} />)}
            />

        </React.StrictMode>;
    }

    private renderSidebar(): React.ReactNode {
        const { minTimestamp, maxTimestamp } = this.props;
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
                        onChange={minTimestamp => this.applyFilter({ minTimestamp })} />

                    <TimeFilter
                        text={"Event created before"}
                        selected={maxTimestamp}
                        onChange={maxTimestamp => this.applyFilter({ maxTimestamp })} />

                </form>
            </>
        );
    }

    private renderQuickFilters(): React.ReactNode {
        const now = new Date();
        const startOfToday = getStartOfDay(now);
        const startOfWeek = getStartOfWeek(now);
        const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - 1));

        return <>
            <Heading.h3>Quick Filters</Heading.h3>
            <Box mb={16}>
                {this.filter("Today", { minTimestamp: startOfToday, maxTimestamp: undefined })}
                {this.filter("Yesterday", { maxTimestamp: startOfToday, minTimestamp: startOfYesterday })}
                {this.filter("This week", { minTimestamp: startOfWeek, maxTimestamp: undefined })}
                {this.filter("No filter", { minTimestamp: undefined, maxTimestamp: undefined, type: undefined })}
            </Box>
        </>;
    }

    private filter(title: string, filter: Partial<ActivityFilter>) {
        return <BaseLink
            style={{ display: "block" }}
            href={"javascript:void(0)"}
            onClick={() => this.applyFilter(filter)}>{title}</BaseLink>
    }

    private applyFilter(filter: Partial<ActivityFilter>) {
        this.props.updateFilter(filter);
        this.props.resetActivity();
        this.props.fetchActivity({ scrollSize }, filter);
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
    fetchActivity: async (req, filter) => {
        dispatch(setLoading(true));
        dispatch(await fetchActivity(req, filter));
    },

    onMount: () => {
        dispatch(updatePageTitle("Activity"))
        dispatch(setActivePage(SidebarPages.Activity))
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