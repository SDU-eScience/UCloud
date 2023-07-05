import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {useCallback} from "react";
import * as UCloud from "@/UCloud";
import {MainContainer} from "@/MainContainer/MainContainer";
import {
    useRefreshFunction
} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import * as Pagination from "@/Pagination";
import * as Heading from "@/ui-components/Heading";
import {Spacer} from "@/ui-components/Spacer";
import {getQueryParam, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import * as Pages from "./Pages";
import {usePrioritizedSearch} from "@/Utilities/SearchUtilities";
import {emptyPage} from "@/DefaultObjects";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import {GridCardGroup} from "@/ui-components/Grid";
import {AppCard, ApplicationCardType} from "./Card";
import {Link} from "@/ui-components";

export const Applications: React.FunctionComponent = () => {
    useTitle("Applications");
    usePrioritizedSearch("applications");
    const location = useLocation();

    const [appResp, fetchApps] = useCloudAPI<UCloud.Page<ApplicationSummaryWithFavorite>>(
        {noop: true},
        emptyPage
    );

    const navigate = useNavigate();
    const page = parseInt(getQueryParamOrElse(location.search, "page", "0"), 10);
    const itemsPerPage = parseInt(getQueryParamOrElse(location.search, "itemsPerPage", "25"), 10);
    const tag = getQueryParam(location.search, "tag");

    const fetch = useCallback(() => {
        if (tag != null) {
            fetchApps(UCloud.compute.apps.searchTags({itemsPerPage, page, query: tag}));
        } else {
            fetchApps(UCloud.compute.apps.listAll({itemsPerPage, page}));
        }
    }, [location.search]);

    const goToPage = useCallback((page: number, itemsPerPage: number) => {
        if (tag === null) {
            navigate(Pages.browse(itemsPerPage, page));
        } else {
            navigate(Pages.browseByTag(tag, itemsPerPage, page));
        }
    }, [tag, history]);

    useRefreshFunction(fetch);

    React.useEffect(() => {
        fetch();
    }, [location]);

    const [, invokeCommand] = useCloudCommand();
    const toggleFavorite = useCallback(async (appName: string, appVersion: string) => {
        await invokeCommand(UCloud.compute.apps.toggleFavorite({appName, appVersion}));
        fetch();
    }, [fetch]);

    return (
        <MainContainer
            header={(
                <Spacer
                    left={(<Heading.h2 style={{marginTop: "0", marginLeft: "15px"}}>{tag}</Heading.h2>)}
                    right={(
                        <Pagination.EntriesPerPageSelector
                            content="Apps per page"
                            entriesPerPage={itemsPerPage}
                            onChange={itemsPerPage => goToPage(0, itemsPerPage)}
                        />
                    )}
                />
            )}
            main={
                <Pagination.List
                    loading={appResp.loading}
                    pageRenderer={page =>
                        <GridCardGroup minmax={156} gridGap={15}>
                            {page.items.map(it => (
                                <Link to={Pages.run(it.metadata.name, it.metadata.version)}>
                                    <AppCard
                                        type={ApplicationCardType.TALL}
                                        onFavorite={toggleFavorite}
                                        app={it}
                                        key={it.metadata.name + "-" + it.metadata.version}
                                        isFavorite={it.favorite}
                                        tags={it.tags}
                                    />
                                </Link>
                            ))}
                        </GridCardGroup>
                    }
                    page={appResp.data}
                    onPageChanged={pageNumber => goToPage(pageNumber, itemsPerPage)}
                />
            }
        />
    );
};

export default Applications;
