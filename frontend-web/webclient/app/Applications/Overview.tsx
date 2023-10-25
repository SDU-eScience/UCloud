import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Flex, Icon, Input, Link} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {Spacer} from "@/ui-components/Spacer";
import {ApplicationCardType, FavoriteApp} from "./Card";
import * as Pages from "./Pages";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreSections = compute.AppStoreSections;
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {useLocation, useNavigate} from "react-router";
import AppRoutes from "@/Routes";
import {ApplicationGroup} from "./api";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow from "./ApplicationsRow";
import {FavoriteAppRow} from "./Landing";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

export const ShowAllTagItem: React.FunctionComponent<{tag?: string; children: React.ReactNode;}> = props => (
    <Link to={props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

function favoriteStatusKey(metadata: compute.ApplicationMetadata): string {
    return `${metadata.name}/${metadata.version}`;
}

type FavoriteStatus = Record<string, {override: boolean, app: ApplicationSummaryWithFavorite}>;

const LargeSearchBoxClass = injectStyle("large-search-box", k => `
    ${k} {
        width: 400px;
        margin: 30px auto;
        position: relative;
    }

    ${k} input {
        width: 100%;
        border: 1px solid var(--midGray);
        background: var(--white);
        border-radius: 99px;
        padding-left: 1.2em;
        padding-right: 2.5rem;
    }

    ${k} button {
        background: none;
        border: 0;
        padding: 2px 10px 1px 10px;
        cursor: pointer;
        position: absolute;
        right: 0;
        height: 2.5rem;
    }
`);

export const LargeSearchBox: React.FunctionComponent<{value?: string}> = props => {
    const navigate = useNavigate();
  
    return <div className={LargeSearchBoxClass}>
        <Flex justifyContent="space-evenly">
            <Input
                defaultValue={props.value}
                placeholder="Search for applications..."
                onKeyUp={e => {
                    console.log(e);
                    if (e.key === "Enter") {
                        navigate(AppRoutes.apps.search((e as unknown as {target: {value: string}}).target.value));
                    }
                }}
                autoFocus
            />
            <button>
                <Icon name="search" size={20} color="darkGray" my="auto" />
            </button>
        </Flex>
    </div>;
}

const ApplicationsOverview: React.FunctionComponent = () => {
    const [sections, fetchSections] = useCloudAPI<AppStoreSections>(
        {noop: true},
        {sections: []}
    );

    const location = useLocation();

    useEffect(() => {
        const hash = location.hash;
        const el = hash && document.getElementById(hash.slice(1))
        if (el) {
            el.scrollIntoView({behavior: "smooth"});
        }

    })

    const [refreshId, setRefreshId] = useState<number>(0);

    useEffect(() => {
        fetchSections(UCloud.compute.apps.appStoreSections({page: "FULL"}));
    }, [refreshId]);

    useResourceSearch(ApiLike);

    const dispatch = useDispatch();

    useTitle("Applications");
    const refresh = useCallback(() => {
        setRefreshId(id => id + 1);
    }, []);
    useRefreshFunction(refresh);

    const [, invokeCommand] = useCloudCommand();
    const favoriteStatus = React.useRef<FavoriteStatus>({});

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        // Note(Jonas): This used to check commandLoading (from invokeCommand), but this gets stuck at true, so removed for now.
        const key = favoriteStatusKey(app.metadata);
        const isFavorite = favoriteStatus.current[key]?.override ?? app.favorite;
        if (favoriteStatus.current[key]) {
            delete favoriteStatus.current[key]
        } else {
            favoriteStatus.current[key] = {override: !isFavorite, app};
        }
        favoriteStatus.current = {...favoriteStatus.current};
        dispatch(toggleAppFavorite(app, !isFavorite));
        try {
            await invokeCommand(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name,
                appVersion: app.metadata.version
            }));
        } catch (e) {
            favoriteStatus.current[key].override = !favoriteStatus.current[key].override;
            favoriteStatus.current = {...favoriteStatus.current};
        }
    }, [favoriteStatus]);

    return (
        <div className={AppOverviewMarginPaddingHack}>
            <MainContainer main={
                <Box mx="auto" maxWidth="1340px">
                    <Flex width="100%">
                        <Box ml="auto" mt="30px">
                            <ContextSwitcher />
                        </Box>
                    </Flex>
                    <Box mt="12px" />
                    <FavoriteAppRow
                        favoriteStatus={favoriteStatus}
                        onFavorite={onFavorite}
                        refreshId={refreshId}
                    />

                    <LargeSearchBox />

                    {sections.data.sections.map(section =>
                        <div key={section.name} id={"section"+section.id.toString()}>
                            <Spacer
                                /* seeing as there's no `right`, can't this just be a normal heading with styling? */
                                mt="15px" px="10px" alignItems={"center"}
                                left={<Heading.h2>{section.name}</Heading.h2>}
                                right={<></>}
                            />

                            <ApplicationRow
                                items={section.featured}
                                type={ApplicationCardType.WIDE}
                                refreshId={refreshId}
                                scrolling={false}
                            />

                            <ApplicationRow
                                items={section.items}
                                type={ApplicationCardType.TALL}
                                refreshId={refreshId}
                                scrolling={true}
                            />
                        </div>
                    )}
                </Box>
            } />
        </div>
    );
};

const AppOverviewMarginPaddingHack = injectStyleSimple("HACK-HACK-HACK", `
/* HACK */
    margin-top: -12px;
/* HACK */
`);

export default ApplicationsOverview;