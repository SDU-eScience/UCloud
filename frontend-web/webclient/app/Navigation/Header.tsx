import {defaultSearch, defaultSearchPlaceholder, HeaderSearchType, KeyCode} from "@/DefaultObjects";
import {HeaderStateToProps} from "@/Navigation";
import {setPrioritizedSearch} from "@/Navigation/Redux/HeaderActions";
import * as React from "react";
import {connect} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {Dispatch} from "redux";
import styled from "styled-components";
import * as ui from "@/ui-components";
import {Dropdown} from "@/ui-components/Dropdown";
import Link from "@/ui-components/Link";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {doNothing} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useEffect, useRef} from "react";

interface HeaderProps extends HeaderStateToProps, HeaderOperations {
    toggleTheme(): void;
}

// Note(Jonas): We need something similar NOT in the header. Or maybe keep this? Nah.
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

function Header(): JSX.Element | null {
    if (Math.random()) return null;

    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Search />
        </HeaderContainer>
    );
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
const _Search = (): JSX.Element => {
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
