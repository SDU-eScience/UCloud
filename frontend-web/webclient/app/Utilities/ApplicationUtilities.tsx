import { infoNotification } from "UtilityFunctions";
import { Application } from "Applications";
import Cloud from "Authentication/lib";
import { Page} from "Types";

export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `hpc/jobs/follow/${id}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

/**
* //FIXME Missing backend functionality
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = (application: Application, page: Page<Application>, cloud: Cloud): Page<Application> => {
    const a = page.items.find(it => it.description.info.name === application.description.info.name);
    a.favorite = !a.favorite;
    infoNotification("Backend functionality for favoriting applications missing");
    return page;
    /*  
    const { info } = a.description;
    if (a.favorite) {
        cloud.post(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } else {
        cloud.delete(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } 
    */
}