//
// Generated by `./run.sh --run-script api-gen` from the backend. Do not modify this file!
//

import { CallParameters } from "@/Authentication/CallParameters";

export function estimateRpcName(params: CallParameters): string | null {
    const method = params.method;
    let path = params.path;
    const queryBegin = path.indexOf("?");
    const beforeQuery = queryBegin === -1 ? path : path.substring(0, queryBegin);
    path = beforeQuery;
    if (beforeQuery.length > 1 && beforeQuery[beforeQuery.length - 1] === '/') {
        path = beforeQuery.substring(0, beforeQuery.length - 1);
    }
    if (params.context === undefined) {
        path = "/api" + path;
    } else if (params.context !== "") {
        path = params.context + path;
    }
    
    path = "/" + path.split("/").filter(it => it.trim().length > 0).join("/");
    
    switch (method) {
case 'DELETE':
switch (path) {
case '/api/notifications': return 'notifications.delete';
case '/api/licenses': return 'licenses.delete';
case '/auth/sessions': return 'auth.invalidateSessions';
case '/api/gifts': return 'gifts.deleteGift';
case '/api/ingresses': return 'ingresses.delete';
case '/api/files': return 'files.delete';
case '/api/news/delete': return 'news.deletePost';
case '/api/files/metadata': return 'files.metadata.delete';
case '/api/projects/groups': return 'project.group.delete';
case '/api/projects/groups/members': return 'project.group.removeGroupMember';
case '/api/files/collections': return 'files.collections.delete';
case '/api/networkips': return 'networkips.delete';
case '/api/projects/invites/reject': return 'project.rejectInvite';
case '/api/projects/leave': return 'project.leaveProject';
case '/api/projects/members': return 'project.deleteMember';
case '/api/shares': return 'shares.delete';
}
break;
case 'POST':
switch (path) {
case '/auth/providers': return 'auth.providers.register';
case '/auth/providers/claim': return 'auth.providers.claim';
case '/auth/providers/renew': return 'auth.providers.renew';
case '/auth/providers/refresh': return 'auth.providers.refresh';
case '/auth/providers/refreshAsOrchestrator': return 'auth.providers.refreshAsOrchestrator';
case '/auth/providers/generateKeyPair': return 'auth.providers.generateKeyPair';
case '/auth/2fa': return 'auth.twofactor.createCredentials';
case '/auth/2fa/challenge': return 'auth.twofactor.answerChallenge';
case '/api/mail/support': return 'mail.sendSupport';
case '/api/mail/sendToUser': return 'mail.sendToUser';
case '/api/mail/sendDirect': return 'mail.sendDirect';
case '/api/mail/toggleEmailSettings': return 'mail.toggleEmailSettings';
case '/api/notifications/read': return 'notifications.markAsRead';
case '/api/notifications/read/all': return 'notifications.markAllAsRead';
case '/api/notifications/bulk': return 'notifications.createBulk';
case '/api/notifications/settings': return 'notifications.updateSettings';
case '/api/tasks/postStatus': return 'task.postStatus';
case '/api/tasks/markAsComplete': return 'task.markAsComplete';
case '/api/licenses': return 'licenses.create';
case '/api/licenses/search': return 'licenses.search';
case '/api/licenses/init': return 'licenses.init';
case '/api/licenses/updateAcl': return 'licenses.updateAcl';
case '/api/products/v2': return 'products.v2.create';
case '/api/password/reset': return 'password.reset.reset';
case '/api/password/reset/new': return 'password.reset.newPassword';
case '/auth/refresh': return 'auth.refresh';
case '/auth/refresh/web': return 'auth.webRefresh';
case '/auth/logout/bulk': return 'auth.bulkInvalidate';
case '/auth/logout': return 'auth.logout';
case '/auth/logout/web': return 'auth.webLogout';
case '/auth/claim': return 'auth.claim';
case '/auth/request': return 'auth.requestOneTimeTokenWithAudience';
case '/auth/extend': return 'auth.tokenExtension';
case '/auth/login': return 'auth.passwordLogin';
case '/api/gifts/claim': return 'gifts.claimGift';
case '/api/gifts': return 'gifts.createGift';
case '/api/sla/accept': return 'sla.accept';
case '/api/ingresses': return 'ingresses.create';
case '/api/ingresses/search': return 'ingresses.search';
case '/api/ingresses/init': return 'ingresses.init';
case '/api/ingresses/updateAcl': return 'ingresses.updateAcl';
case '/api/avatar/update': return 'avatar.update';
case '/api/avatar/bulk': return 'avatar.findBulk';
case '/api/jobs/terminate': return 'jobs.terminate';
case '/api/jobs/extend': return 'jobs.extend';
case '/api/jobs/suspend': return 'jobs.suspend';
case '/api/jobs/unsuspend': return 'jobs.unsuspend';
case '/api/jobs/interactiveSession': return 'jobs.openInteractiveSession';
case '/api/jobs': return 'jobs.create';
case '/api/jobs/search': return 'jobs.search';
case '/api/jobs/init': return 'jobs.init';
case '/api/jobs/updateAcl': return 'jobs.updateAcl';
case '/api/files/metadataTemplates/templates': return 'files.metadataTemplates.createTemplate';
case '/api/files/metadataTemplates/deprecate': return 'files.metadataTemplates.deprecate';
case '/api/files/metadataTemplates': return 'files.metadataTemplates.create';
case '/api/files/metadataTemplates/init': return 'files.metadataTemplates.init';
case '/api/files/metadataTemplates/updateAcl': return 'files.metadataTemplates.updateAcl';
case '/api/slack/sendAlert': return 'slack.sendAlert';
case '/api/slack/sendSupport': return 'slack.sendSupport';
case '/api/licenses/control/chargeCredits': return 'licenses.control.chargeCredits';
case '/api/licenses/control/checkCredits': return 'licenses.control.checkCredits';
case '/api/licenses/control/register': return 'licenses.control.register';
case '/api/licenses/control/update': return 'licenses.control.update';
case '/api/support/ticket': return 'support.createTicket';
case '/api/jobs/control/browseSshKeys': return 'jobs.control.browseSshKeys';
case '/api/jobs/control/update': return 'jobs.control.update';
case '/api/jobs/control/chargeCredits': return 'jobs.control.chargeCredits';
case '/api/jobs/control/checkCredits': return 'jobs.control.checkCredits';
case '/api/jobs/control/register': return 'jobs.control.register';
case '/api/files/control/addUpdate': return 'files.control.addUpdate';
case '/api/files/control/markAsComplete': return 'files.control.markAsComplete';
case '/api/projects/v2': return 'projects.v2.create';
case '/api/projects/v2/archive': return 'projects.v2.archive';
case '/api/projects/v2/unarchive': return 'projects.v2.unarchive';
case '/api/projects/v2/toggleFavorite': return 'projects.v2.toggleFavorite';
case '/api/projects/v2/updateSettings': return 'projects.v2.updateSettings';
case '/api/projects/v2/retrieveAllUsersGroup': return 'projects.v2.retrieveAllUsersGroup';
case '/api/projects/v2/renameProject': return 'projects.v2.renameProject';
case '/api/projects/v2/projectVerificationStatus': return 'projects.v2.projectVerificationStatus';
case '/api/projects/v2/verifyMembership': return 'projects.v2.verifyMembership';
case '/api/projects/v2/invites': return 'projects.v2.createInvite';
case '/api/projects/v2/acceptInvite': return 'projects.v2.acceptInvite';
case '/api/projects/v2/deleteInvite': return 'projects.v2.deleteInvite';
case '/api/projects/v2/link': return 'projects.v2.createInviteLink';
case '/api/projects/v2/deleteInviteLink': return 'projects.v2.deleteInviteLink';
case '/api/projects/v2/updateInviteLink': return 'projects.v2.updateInviteLink';
case '/api/projects/v2/acceptInviteLink': return 'projects.v2.acceptInviteLink';
case '/api/projects/v2/deleteMember': return 'projects.v2.deleteMember';
case '/api/projects/v2/changeRole': return 'projects.v2.changeRole';
case '/api/projects/v2/groups': return 'projects.v2.createGroup';
case '/api/projects/v2/renameGroup': return 'projects.v2.renameGroup';
case '/api/projects/v2/deleteGroup': return 'projects.v2.deleteGroup';
case '/api/projects/v2/groupMembers': return 'projects.v2.createGroupMember';
case '/api/projects/v2/deleteGroupMember': return 'projects.v2.deleteGroupMember';
case '/api/shares/control/chargeCredits': return 'shares.control.chargeCredits';
case '/api/shares/control/checkCredits': return 'shares.control.checkCredits';
case '/api/shares/control/register': return 'shares.control.register';
case '/api/shares/control/update': return 'shares.control.update';
case '/api/grants/v2/submitRevision': return 'grants.v2.submitRevision';
case '/api/grants/v2/updateState': return 'grants.v2.updateState';
case '/api/grants/v2/transfer': return 'grants.v2.transfer';
case '/api/grants/v2/retrieveGrantGivers': return 'grants.v2.retrieveGrantGivers';
case '/api/grants/v2/postComment': return 'grants.v2.postComment';
case '/api/grants/v2/deleteComment': return 'grants.v2.deleteComment';
case '/api/grants/v2/updateRequestSettings': return 'grants.v2.updateRequestSettings';
case '/api/grants/v2/uploadLogo': return 'grants.v2.uploadLogo';
case '/api/files/move': return 'files.move';
case '/api/files/copy': return 'files.copy';
case '/api/files/upload': return 'files.createUpload';
case '/api/files/download': return 'files.createDownload';
case '/api/files/folder': return 'files.createFolder';
case '/api/files/trash': return 'files.trash';
case '/api/files/emptyTrash': return 'files.emptyTrash';
case '/api/files/updateAcl': return 'files.updateAcl';
case '/api/files/search': return 'files.search';
case '/api/files/init': return 'files.init';
case '/api/ingresses/control/chargeCredits': return 'ingresses.control.chargeCredits';
case '/api/ingresses/control/checkCredits': return 'ingresses.control.checkCredits';
case '/api/ingresses/control/register': return 'ingresses.control.register';
case '/api/ingresses/control/update': return 'ingresses.control.update';
case '/api/projects/membership': return 'project.members.userStatus';
case '/api/projects/membership/lookup-admins': return 'project.members.lookupAdminsBulk';
case '/api/networkips/control/chargeCredits': return 'networkips.control.chargeCredits';
case '/api/networkips/control/checkCredits': return 'networkips.control.checkCredits';
case '/api/networkips/control/register': return 'networkips.control.register';
case '/api/networkips/control/update': return 'networkips.control.update';
case '/api/news/update': return 'news.updatePost';
case '/api/news/toggleHidden': return 'news.togglePostHidden';
case '/api/accounting/v2/rootAllocate': return 'accounting.v2.rootAllocate';
case '/api/accounting/v2/updateAllocation': return 'accounting.v2.updateAllocation';
case '/api/accounting/v2/browseWalletsInternal': return 'accounting.v2.browseWalletsInternal';
case '/api/accounting/v2/reportUsage': return 'accounting.v2.reportUsage';
case '/api/accounting/v2/checkProviderUsable': return 'accounting.v2.checkProviderUsable';
case '/api/accounting/v2/findRelevantProviders': return 'accounting.v2.findRelevantProviders';
case '/api/accounting/v2/browseProviderAllocations': return 'accounting.v2.browseProviderAllocations';
case '/api/files/metadata': return 'files.metadata.create';
case '/api/files/metadata/move': return 'files.metadata.moveMetadata';
case '/api/files/metadata/approve': return 'files.metadata.approve';
case '/api/files/metadata/reject': return 'files.metadata.reject';
case '/api/files/collections/control/chargeCredits': return 'files.collections.control.chargeCredits';
case '/api/files/collections/control/checkCredits': return 'files.collections.control.checkCredits';
case '/api/files/collections/control/register': return 'files.collections.control.register';
case '/api/files/collections/control/update': return 'files.collections.control.update';
case '/api/projects/groups/list-all-group-members': return 'project.group.listAllGroupMembers';
case '/api/projects/groups/update-name': return 'project.group.updateGroupName';
case '/api/projects/groups/is-member': return 'project.group.isMember';
case '/api/projects/groups/exists': return 'project.group.groupExists';
case '/api/providers/update': return 'providers.update';
case '/api/providers/renewToken': return 'providers.renewToken';
case '/api/providers/requestApproval': return 'providers.requestApproval';
case '/api/providers/approve': return 'providers.approve';
case '/api/providers': return 'providers.create';
case '/api/providers/search': return 'providers.search';
case '/api/providers/init': return 'providers.init';
case '/api/providers/updateAcl': return 'providers.updateAcl';
case '/api/files/collections/rename': return 'files.collections.rename';
case '/api/files/collections': return 'files.collections.create';
case '/api/files/collections/search': return 'files.collections.search';
case '/api/files/collections/init': return 'files.collections.init';
case '/api/files/collections/updateAcl': return 'files.collections.updateAcl';
case '/api/hpc/apps/search': return 'hpc.apps.search';
case '/api/hpc/apps/openWith': return 'hpc.apps.browseOpenWithRecommendations';
case '/api/hpc/apps/updateApplicationFlavor': return 'hpc.apps.updateApplicationFlavor';
case '/api/hpc/apps/updateAcl': return 'hpc.apps.updateAcl';
case '/api/hpc/apps/updatePublicFlag': return 'hpc.apps.updatePublicFlag';
case '/api/hpc/apps/toggleStar': return 'hpc.apps.toggleStar';
case '/api/hpc/apps/createGroup': return 'hpc.apps.createGroup';
case '/api/hpc/apps/updateGroup': return 'hpc.apps.updateGroup';
case '/api/hpc/apps/deleteGroup': return 'hpc.apps.deleteGroup';
case '/api/hpc/apps/uploadLogo': return 'hpc.apps.addLogoToGroup';
case '/api/hpc/apps/removeLogoFromGroup': return 'hpc.apps.removeLogoFromGroup';
case '/api/hpc/apps/assignApplicationToGroup': return 'hpc.apps.assignApplicationToGroup';
case '/api/hpc/apps/createCategory': return 'hpc.apps.createCategory';
case '/api/hpc/apps/addGroupToCategory': return 'hpc.apps.addGroupToCategory';
case '/api/hpc/apps/removeGroupFromCategory': return 'hpc.apps.removeGroupFromCategory';
case '/api/hpc/apps/assignPriorityToCategory': return 'hpc.apps.assignPriorityToCategory';
case '/api/hpc/apps/deleteCategory': return 'hpc.apps.deleteCategory';
case '/api/hpc/apps/createSpotlight': return 'hpc.apps.createSpotlight';
case '/api/hpc/apps/updateSpotlight': return 'hpc.apps.updateSpotlight';
case '/api/hpc/apps/deleteSpotlight': return 'hpc.apps.deleteSpotlight';
case '/api/hpc/apps/activateSpotlight': return 'hpc.apps.activateSpotlight';
case '/api/hpc/apps/updateCarrousel': return 'hpc.apps.updateCarrousel';
case '/api/hpc/apps/updateCarrouselImage': return 'hpc.apps.updateCarrouselImage';
case '/api/hpc/apps/updateTopPicks': return 'hpc.apps.updateTopPicks';
case '/api/hpc/apps/devImport': return 'hpc.apps.devImport';
case '/api/hpc/apps/importFromFile': return 'hpc.apps.importFromFile';
case '/api/hpc/apps/export': return 'hpc.apps.export';
case '/api/networkips/firewall': return 'networkips.updateFirewall';
case '/api/networkips': return 'networkips.create';
case '/api/networkips/search': return 'networkips.search';
case '/api/networkips/init': return 'networkips.init';
case '/api/networkips/updateAcl': return 'networkips.updateAcl';
case '/api/products': return 'products.create';
case '/auth/users/register': return 'auth.users.createNewUser';
case '/auth/users/updateUserInfo': return 'auth.users.updateUserInfo';
case '/auth/users/password': return 'auth.users.changePassword';
case '/auth/users/password/reset': return 'auth.users.changePasswordWithReset';
case '/auth/users/lookup': return 'auth.users.lookupUsers';
case '/auth/users/lookup/email': return 'auth.users.lookupEmail';
case '/auth/users/lookup/with-email': return 'auth.users.lookupUserWithEmail';
case '/auth/users/optionalInfo': return 'auth.users.updateOptionalUserInfo';
case '/api/projects/favorite': return 'project.favorite.toggleFavorite';
case '/placeholder': return 'PROVIDERID.uploadChunk';
case '/api/iapps/syncthing/reset': return 'iapps.syncthing.resetConfiguration';
case '/api/iapps/syncthing/restart': return 'iapps.syncthing.restart';
case '/api/iapps/syncthing/update': return 'iapps.syncthing.updateConfiguration';
case '/api/projects': return 'project.create';
case '/api/projects/invites': return 'project.invite';
case '/api/projects/invites/accept': return 'project.acceptInvite';
case '/api/projects/transfer-pi': return 'project.transferPiRole';
case '/api/projects/members/change-role': return 'project.changeUserRole';
case '/api/projects/verify-membership': return 'project.verifyMembership';
case '/api/projects/archive': return 'project.archive';
case '/api/projects/archiveBulk': return 'project.archiveBulk';
case '/api/projects/exists': return 'project.exists';
case '/api/projects/lookupByIdBulk': return 'project.lookupByIdBulk';
case '/api/projects/rename': return 'project.rename';
case '/api/projects/toggleRenaming': return 'project.toggleRenaming';
case '/api/projects/update-dmp': return 'project.updateDataManagementPlan';
case '/api/projects/search': return 'project.search';
case '/api/shares/approve': return 'shares.approve';
case '/api/shares/reject': return 'shares.reject';
case '/api/shares/permissions': return 'shares.updatePermissions';
case '/api/shares': return 'shares.create';
case '/api/shares/search': return 'shares.search';
case '/api/shares/init': return 'shares.init';
case '/api/shares/updateAcl': return 'shares.updateAcl';
}
break;
case 'GET':
switch (path) {
case '/auth/providers/retrieveKey': return 'auth.providers.retrievePublicKey';
case '/auth/2fa/status': return 'auth.twofactor.twoFactorStatus';
case '/api/mail/retrieveEmailSettings': return 'mail.retrieveEmailSettings';
case '/api/notifications': return 'notifications.list';
case '/api/notifications/retrieveSettings': return 'notifications.retrieveSettings';
case '/api/tasks': return 'task.list';
case '/api/tasks/retrieve': return 'task.view';
case '/api/licenses/retrieveProducts': return 'licenses.retrieveProducts';
case '/api/licenses/retrieve': return 'licenses.retrieve';
case '/api/licenses/browse': return 'licenses.browse';
case '/api/products/v2/retrieve': return 'products.v2.retrieve';
case '/api/products/v2/browse': return 'products.v2.browse';
case '/auth/sessions': return 'auth.listUserSessions';
case '/auth/browseIdentityProviders': return 'auth.browseIdentityProviders';
case '/auth/startLogin': return 'auth.startLogin';
case '/api/gifts/available': return 'gifts.availableGifts';
case '/api/gifts/browse': return 'gifts.browse';
case '/api/sla': return 'sla.find';
case '/api/ingresses/retrieve': return 'ingresses.retrieve';
case '/api/ingresses/retrieveProducts': return 'ingresses.retrieveProducts';
case '/api/ingresses/browse': return 'ingresses.browse';
case '/api/avatar/find': return 'avatar.findAvatar';
case '/api/jobs/retrieveUtilization': return 'jobs.retrieveUtilization';
case '/api/jobs/retrieveProducts': return 'jobs.retrieveProducts';
case '/api/jobs/retrieve': return 'jobs.retrieve';
case '/api/jobs/browse': return 'jobs.browse';
case '/api/files/metadataTemplates/retrieveLatest': return 'files.metadataTemplates.retrieveLatest';
case '/api/files/metadataTemplates/retrieveTemplates': return 'files.metadataTemplates.retrieveTemplate';
case '/api/files/metadataTemplates/browseTemplates': return 'files.metadataTemplates.browseTemplates';
case '/api/files/metadataTemplates/browse': return 'files.metadataTemplates.browse';
case '/api/files/metadataTemplates/retrieve': return 'files.metadataTemplates.retrieve';
case '/api/files/metadataTemplates/retrieveProducts': return 'files.metadataTemplates.retrieveProducts';
case '/api/licenses/control/browse': return 'licenses.control.browse';
case '/api/licenses/control/retrieve': return 'licenses.control.retrieve';
case '/api/jobs/control/browse': return 'jobs.control.browse';
case '/api/jobs/control/retrieve': return 'jobs.control.retrieve';
case '/api/projects/v2/retrieve': return 'projects.v2.retrieve';
case '/api/projects/v2/browse': return 'projects.v2.browse';
case '/api/projects/v2/browseInvites': return 'projects.v2.browseInvites';
case '/api/projects/v2/browseLink': return 'projects.v2.browseInviteLinks';
case '/api/projects/v2/retrieveLink': return 'projects.v2.retrieveInviteLinkProject';
case '/api/projects/v2/retrieveGroups': return 'projects.v2.retrieveGroup';
case '/api/projects/v2/retrieveProviderProject': return 'projects.v2.retrieveProviderProject';
case '/api/projects/v2/retrieveProviderProjectInternal': return 'projects.v2.retrieveProviderProjectInternal';
case '/api/shares/control/browse': return 'shares.control.browse';
case '/api/shares/control/retrieve': return 'shares.control.retrieve';
case '/api/grants/v2/browse': return 'grants.v2.browse';
case '/api/grants/v2/retrieve': return 'grants.v2.retrieve';
case '/api/grants/v2/retrieveRequestSettings': return 'grants.v2.retrieveRequestSettings';
case '/api/grants/v2/retrieveLogo': return 'grants.v2.retrieveLogo';
case '/api/files/browse': return 'files.browse';
case '/api/files/retrieve': return 'files.retrieve';
case '/api/files/retrieveProducts': return 'files.retrieveProducts';
case '/api/ingresses/control/browse': return 'ingresses.control.browse';
case '/api/ingresses/control/retrieve': return 'ingresses.control.retrieve';
case '/api/projects/membership/search': return 'project.members.search';
case '/api/projects/membership/count': return 'project.members.count';
case '/api/projects/membership/lookup-admins': return 'project.members.lookupAdmins';
case '/api/networkips/control/browse': return 'networkips.control.browse';
case '/api/networkips/control/retrieve': return 'networkips.control.retrieve';
case '/api/news/listCategories': return 'news.listCategories';
case '/api/news/list': return 'news.listPosts';
case '/api/news/listDowntimes': return 'news.listDowntimes';
case '/api/news/byId': return 'news.getPostBy';
case '/api/accounting/v2/browseWallets': return 'accounting.v2.browseWallets';
case '/api/accounting/v2/retrieveRetrieveDescendants': return 'accounting.v2.retrieveDescendants';
case '/api/files/metadata/retrieveAll': return 'files.metadata.retrieveAll';
case '/api/files/metadata/browse': return 'files.metadata.browse';
case '/api/files/collections/control/browse': return 'files.collections.control.browse';
case '/api/files/collections/control/retrieve': return 'files.collections.control.retrieve';
case '/api/projects/groups/summary': return 'project.group.listGroupsWithSummary';
case '/api/projects/groups/members': return 'project.group.listGroupMembers';
case '/api/projects/groups/count': return 'project.group.count';
case '/api/projects/groups/view': return 'project.group.view';
case '/api/projects/groups/lookup-by-title': return 'project.group.lookupByTitle';
case '/api/projects/groups/lookup-project-and-group': return 'project.group.lookupProjectAndGroup';
case '/api/projects/groups/list-all-groups': return 'project.group.listAllGroupIdsAndTitles';
case '/api/providers/retrieveSpecification': return 'providers.retrieveSpecification';
case '/api/providers/browse': return 'providers.browse';
case '/api/providers/retrieve': return 'providers.retrieve';
case '/api/providers/retrieveProducts': return 'providers.retrieveProducts';
case '/api/files/collections/retrieve': return 'files.collections.retrieve';
case '/api/files/collections/retrieveProducts': return 'files.collections.retrieveProducts';
case '/api/files/collections/browse': return 'files.collections.browse';
case '/api/hpc/apps/byName': return 'hpc.apps.findByName';
case '/api/hpc/apps/byNameAndVersion': return 'hpc.apps.findByNameAndVersion';
case '/api/hpc/apps/retrieveAcl': return 'hpc.apps.retrieveAcl';
case '/api/hpc/apps/retrieveAllApplications': return 'hpc.apps.listAllApplications';
case '/api/hpc/apps/retrieveStars': return 'hpc.apps.retrieveStars';
case '/api/hpc/apps/retrieveGroups': return 'hpc.apps.retrieveGroup';
case '/api/hpc/apps/browseGroups': return 'hpc.apps.browseGroups';
case '/api/hpc/apps/retrieveGroupLogo': return 'hpc.apps.retrieveGroupLogo';
case '/api/hpc/apps/retrieveAppLogo': return 'hpc.apps.retrieveAppLogo';
case '/api/hpc/apps/browseCategories': return 'hpc.apps.browseCategories';
case '/api/hpc/apps/retrieveCategory': return 'hpc.apps.retrieveCategory';
case '/api/hpc/apps/retrieveLandingPage': return 'hpc.apps.retrieveLandingPage';
case '/api/hpc/apps/retrieveCarrouselImage': return 'hpc.apps.retrieveCarrouselImage';
case '/api/hpc/apps/retrieveSpotlight': return 'hpc.apps.retrieveSpotlight';
case '/api/hpc/apps/browseSpotlight': return 'hpc.apps.browseSpotlight';
case '/api/networkips/retrieveProducts': return 'networkips.retrieveProducts';
case '/api/networkips/retrieve': return 'networkips.retrieve';
case '/api/networkips/browse': return 'networkips.browse';
case '/api/products/retrieve': return 'products.retrieve';
case '/api/products/browse': return 'products.browse';
case '/auth/users/userInfo': return 'auth.users.getUserInfo';
case '/auth/users/verifyUserInfo': return 'auth.users.verifyUserInfo';
case '/auth/users/retrieveOptionalInfo': return 'auth.users.retrieveOptionalUserInfo';
case '/api/hpc/tools/byNameAndVersion': return 'hpc.tools.findByNameAndVersion';
case '/api/hpc/tools/byName': return 'hpc.tools.findByName';
case '/api/iapps/syncthing/retrieve': return 'iapps.syncthing.retrieveConfiguration';
case '/api/projects/members': return 'project.viewMemberInProject';
case '/api/projects/invites/ingoing': return 'project.listIngoingInvites';
case '/api/projects/invites/outgoing': return 'project.listOutgoingInvites';
case '/api/projects/listFavorites': return 'project.listFavoriteProjects';
case '/api/projects/list': return 'project.listProjects';
case '/api/projects/view': return 'project.viewProject';
case '/api/projects/sub-projects': return 'project.listSubProjects';
case '/api/projects/sub-projects-count': return 'project.countSubProjects';
case '/api/projects/ancestors': return 'project.viewAncestors';
case '/api/projects/lookupByTitle': return 'project.lookupByPath';
case '/api/projects/lookupById': return 'project.lookupById';
case '/api/projects/lookup-pi': return 'project.lookupPrincipalInvestigator';
case '/api/projects/renameable': return 'project.allowsRenaming';
case '/api/projects/renameable-sub': return 'project.allowsSubProjectRenaming';
case '/api/projects/dmp': return 'project.fetchDataManagementPlan';
case '/api/shares/browseOutgoing': return 'shares.browseOutgoing';
case '/api/shares/browse': return 'shares.browse';
case '/api/shares/retrieve': return 'shares.retrieve';
case '/api/shares/retrieveProducts': return 'shares.retrieveProducts';
}
break;
case 'PUT':
switch (path) {
case '/api/notifications': return 'notifications.create';
case '/api/tasks': return 'task.create';
case '/api/news/post': return 'news.newPost';
case '/api/projects/groups': return 'project.group.create';
case '/api/projects/groups/members': return 'project.group.addGroupMember';
case '/api/hpc/apps': return 'hpc.apps.create';
case '/api/hpc/tools': return 'hpc.tools.create';
}
break;
}
console.warn('Could not resolve RPC name, this will probably break something! A developer should run `./run.sh --run-script api-gen` from the backend', params);
return null;
}
