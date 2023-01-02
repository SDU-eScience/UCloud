import {Client} from "@/Authentication/HttpClientInstance";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {defaultSearch, defaultSearchPlaceholder, HeaderSearchType, KeyCode} from "@/DefaultObjects";
import {HeaderStateToProps} from "@/Navigation";
import {setPrioritizedSearch} from "@/Navigation/Redux/HeaderActions";
import Notification from "@/Notifications";
import {usePromiseKeeper} from "@/PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {Dispatch} from "redux";
import styled from "styled-components";
import * as ui from "@/ui-components";
import {DevelopmentBadgeBase} from "@/ui-components/Badge";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Dropdown} from "@/ui-components/Dropdown";
import Link from "@/ui-components/Link";
import {TextSpan} from "@/ui-components/Text";
import {ThemeToggler} from "@/ui-components/ThemeToggle";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {
    inDevEnvironment,
    isLightThemeStored,
    useFrameHidden,
    stopPropagationAndPreventDefault, doNothing
} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {NewsPost} from "@/Dashboard/Dashboard";
import {AutomaticGiftClaim} from "@/Services/Gifts/AutomaticGiftClaim";
import {VersionManager} from "@/VersionManager/VersionManager";
import {useGlobal} from "@/Utilities/ReduxHooks";
import BackgroundTasks from "@/Services/BackgroundTasks/BackgroundTask";
import {useEffect, useRef} from "react";
import {ResourceInit} from "@/Services/ResourceInit";
import AppRoutes from "@/Routes";

interface HeaderProps extends HeaderStateToProps, HeaderOperations {
    toggleTheme(): void;
}

const DevelopmentBadge = (): JSX.Element | null => [CONF.DEV_SITE, CONF.STAGING_SITE].includes(window.location.host) ||
    inDevEnvironment() ? <DevelopmentBadgeBase>{window.location.host}</DevelopmentBadgeBase> : null;

export function NonAuthenticatedHeader(): JSX.Element {
    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Logo />
            <ui.Box ml="auto" />
            <ui.Link to="/login">
                <ui.Button color="green" textColor="headerIconColor" mr="12px">Log in</ui.Button>
            </ui.Link>
        </HeaderContainer>
    );
}

function Header(props: HeaderProps): JSX.Element | null {
    const [upcomingDowntime, setUpcomingDowntime] = React.useState(-1);
    const [intervalId, setIntervalId] = React.useState(-1);
    const promises = usePromiseKeeper();

    React.useEffect(() => {
        if (Client.isLoggedIn) {
            props.fetchAvatar();
            fetchDowntimes();
        }
        setIntervalId(window.setInterval(fetchDowntimes, 600_000));
        return () => {
            if (intervalId !== -1) clearInterval(intervalId);
        };
    }, [Client.isLoggedIn]);

    if (useFrameHidden()) return null;
    if (!Client.isLoggedIn) return null;

    const {refresh, spin} = props;

    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Logo />
            <ContextSwitcher />
            <ui.Box ml="auto" />
            <Search />
            <ui.Box mr="auto" />
            {upcomingDowntime !== -1 ? (
                <Link to={AppRoutes.news.detailed(upcomingDowntime)}>
                    <ui.Tooltip
                        right="0"
                        bottom="1"
                        tooltipContentWidth="115px"
                        wrapperOffsetLeft="10px"
                        trigger={<ui.Icon color="yellow" name="warning" />}
                    >
                        Upcoming downtime.<br />
                        Click to view
                    </ui.Tooltip>
                </Link>
            ) : null}
            <ui.Hide xs sm md lg>
                <DevelopmentBadge />
            </ui.Hide>
            <VersionManager />
            <ui.Flex width="48px" justifyContent="center">
                <BackgroundTasks />
            </ui.Flex>
            <ui.Flex width="48px" justifyContent="center">
                <Refresh spin={spin} onClick={refresh} headerLoading={props.statusLoading} />
            </ui.Flex>
            <ui.Support />
            <Notification />
            <AutomaticGiftClaim />
            <ResourceInit/>
            <ClickableDropdown
                width="200px"
                left="-180%"
                trigger={
                    <ui.Flex data-component={"avatar"}>
                        {Client.isLoggedIn ? <UserAvatar avatar={props.avatar} mx={"8px"} /> : null}
                    </ui.Flex>
                }
            >
                {!CONF.STATUS_PAGE ? null : (
                    <>
                        <ui.Box>
                            <ui.ExternalLink color="black" href={CONF.STATUS_PAGE}>
                                <ui.Flex color="black">
                                    <ui.Icon name="favIcon" mr="0.5em" my="0.2em" size="1.3em" />
                                    <TextSpan>Site status</TextSpan>
                                </ui.Flex>
                            </ui.ExternalLink>
                        </ui.Box>
                        <ui.Divider />
                    </>
                )}
                <ui.Box>
                    <Link color="black" to={AppRoutes.users.settings()}>
                        <ui.Flex color="black">
                            <ui.Icon name="properties" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Settings</TextSpan>
                        </ui.Flex>
                    </Link>
                </ui.Box>
                <ui.Flex>
                    <Link to={"/users/avatar"}>
                        <ui.Flex color="black">
                            <ui.Icon name="user" color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Edit Avatar</TextSpan>
                        </ui.Flex>
                    </Link>
                </ui.Flex>
                <ui.Flex onClick={() => Client.logout()} data-component={"logout-button"}>
                    <ui.Icon name="logout" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                    Logout
                </ui.Flex>
                <ui.Divider />
                <span>
                    <ui.Flex cursor="auto">
                        <ThemeToggler
                            isLightTheme={isLightThemeStored()}
                            onClick={onToggleTheme}
                        />
                    </ui.Flex>
                </span>
            </ClickableDropdown>
        </HeaderContainer>
    );

    function onToggleTheme(e: React.SyntheticEvent<HTMLDivElement, Event>): void {
        stopPropagationAndPreventDefault(e);
        props.toggleTheme();
    }

    async function fetchDowntimes(): Promise<void> {
        try {
            if (!Client.isLoggedIn) return;
            const result = await promises.makeCancelable(Client.get<Page<NewsPost>>("/news/listDowntimes")).promise;
            if (result.response.items.length > 0) setUpcomingDowntime(result.response.items[0].id);
        } catch (err) {
            // Ignored
        }
    }
}

export const Refresh = ({
    onClick,
    spin,
    headerLoading
}: {onClick?: () => void; spin: boolean; headerLoading?: boolean}): JSX.Element => !!onClick || headerLoading ? (
    <RefreshIcon
        data-component="refresh"
        data-loading={spin}
        name="refresh"
        spin={spin || headerLoading}
        onClick={onClick}
    />
) : <ui.Box width="24px" />;

const RefreshIcon = styled(ui.Icon)`
  cursor: pointer;
`;

const HeaderContainer = styled(ui.Flex)`
  height: 48px;
  align-items: center;
  position: fixed;
  top: 0;
  width: 100%;
  z-index: 100;
  box-shadow: ${ui.theme.shadows.sm};
`;

const LogoText = styled(ui.Text)`
  vertical-align: top;
  font-weight: 700;
`;

const Logo = (): JSX.Element => (
    <Link
        data-component={"logo"}
        to="/"
        width={[null, null, null, null, null, "190px"]}
    >
        <ui.Flex alignItems="center" ml="15px">
            <ui.Icon name="logoEsc" size="38px" />
            <ui.Text color="headerText" fontSize={4} ml="8px">{CONF.PRODUCT_NAME}</ui.Text>
            {!CONF.VERSION_TEXT ? null : (
                <LogoText
                    ml="4px"
                    mt={-7}
                    color="red"
                    fontSize={17}
                >
                    {CONF.VERSION_TEXT}
                </LogoText>
            )}
        </ui.Flex>
    </Link>
);

const SearchInput = styled(ui.Flex)`
  min-width: 250px;
  width: 425px;
  max-width: 425px;
  height: 36px;
  align-items: center;
  color: white;
  background-color: rgba(236, 239, 244, 0.25);
  border-radius: 5px;

  & > input::placeholder {
    color: white;
  }

  & > input:focus::placeholder {
    color: black;
  }

  & > input:focus ~ div > span > div > svg, input:focus + div > label > svg {
    color: black;
  }

  & > input ~ div > span > div > svg, input + div > label > svg {
    color: white;
  }

  & > input:focus {
    color: black;
    background-color: white;
    transition: ${ui.theme.duration.fast};
  }

  & > input {
    color: white;
    transition: ${ui.theme.duration.fast};
  }

  & > ${Dropdown} > ${ui.Text} > input {
    width: 350px;
    height: 36px;
  }
`;


interface SearchStateProps {
    prioritizedSearch: HeaderSearchType;
}

interface SearchOperations {
    setSearchType: (st: HeaderSearchType) => void;
}

type SearchProps = SearchOperations & SearchStateProps;


// eslint-disable-next-line no-underscore-dangle
const _Search = (props: SearchProps): JSX.Element => {
    const searchRef = useRef<HTMLInputElement>(null);
    const [searchPlaceholder] = useGlobal("searchPlaceholder", defaultSearchPlaceholder);
    const [onSearch] = useGlobal("onSearch", defaultSearch);
    const hasSearch = onSearch !== doNothing;
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        const search = searchRef.current;
        const query = getQueryParamOrElse(location.search, "q", "");
        if (search) search.value = query;
    }, [onSearch]);

    return (<>
        <ui.Hide xs sm md lg>
            <ui.Relative>
                <SearchInput>
                    <ui.Input
                        pl="30px"
                        pr="28px"
                        pt="6px"
                        pb="6px"
                        id="search_input"
                        type="text"
                        overrideDisabledColor={ui.theme.colors.darkBlue}
                        disabled={!hasSearch}
                        placeholder={searchPlaceholder}
                        ref={searchRef}
                        noBorder
                        onKeyDown={e => {
                            if (e.keyCode === KeyCode.ENTER) {
                                if (hasSearch) {
                                    onSearch(searchRef.current!.value, navigate);
                                }
                            }
                        }}
                    />
                    <ui.Absolute left="6px" top="7px">
                        <ui.Label htmlFor="search_input">
                            <ui.Icon name="search" size="20" />
                        </ui.Label>
                    </ui.Absolute>
                </SearchInput>
            </ui.Relative>
        </ui.Hide>
        <ui.Hide xxl xl>
            <ui.Icon
                name="search"
                size="32"
                mr="3px"
                color={hasSearch ? "#FFF" : "gray"}
                cursor={hasSearch ? "pointer" : undefined}
                /* HACK(Jonas): To circumvent the `q === ""` check */
                onClick={() => onSearch(undefined as unknown as string, navigate)}
            />
        </ui.Hide>
    </>
    );
};

export function SmallScreenSearchField(): JSX.Element {
    const [searchPlaceholder] = useGlobal("searchPlaceholder", defaultSearchPlaceholder);
    const [onSearch] = useGlobal("onSearch", defaultSearch);
    const ref = React.useRef<HTMLInputElement>(null);
    const navigate = useNavigate();

    return <ui.Hide xl xxl>
        <form onSubmit={e => (e.preventDefault(), onSearch(ref.current?.value ?? "", navigate))}>
            <ui.Text fontSize="20px" mt="4px">Search</ui.Text>
            <ui.Input
                required
                mt="3px"
                width={"100%"}
                placeholder={searchPlaceholder}
                ref={ref}
            />
            <ui.Button mt="3px" width={"100%"}>Search</ui.Button>
        </form>
    </ui.Hide>;
}

const mapSearchStateToProps = ({
    header,
}: ReduxObject): SearchStateProps => ({
    prioritizedSearch: header.prioritizedSearch,
});

const mapSearchDispatchToProps = (dispatch: Dispatch): SearchOperations => ({
    setSearchType: (st: HeaderSearchType) => dispatch(setPrioritizedSearch(st)),
});

const Search = connect(mapSearchStateToProps, mapSearchDispatchToProps)(_Search);

interface HeaderOperations {
    fetchAvatar: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    fetchAvatar: async () => {
        const action = await findAvatar();
        if (action !== null) dispatch(action);
    },
});

const mapStateToProps = ({header, avatar, ...rest}: ReduxObject): HeaderStateToProps => ({
    ...header,
    avatar,
    spin: isAnyLoading(rest as ReduxObject),
    statusLoading: rest.status.loading
});

const isAnyLoading = (rO: ReduxObject): boolean =>
    rO.loading === true;

export default connect(mapStateToProps, mapDispatchToProps)(Header);
