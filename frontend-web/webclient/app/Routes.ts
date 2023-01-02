const users = {
    settings: () => "/users/settings"
};

const news = {
    detailed: (id: string | number) => `/news/detailed/${id}`,
    list: (filter: string) => `/news/list/${filter}`,
};

const AppRoutes = {
    news,
    users,
};

export default AppRoutes;