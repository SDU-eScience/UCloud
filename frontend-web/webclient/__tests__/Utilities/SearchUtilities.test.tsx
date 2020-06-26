import * as SearchUtils from "Utilities/SearchUtilities";

test("", () => {
    const priority = "APPS";
    const query = "Good apps";
    const page = SearchUtils.searchPage(priority, query);
    expect(page).toBe(`/search/${encodeURIComponent(priority)}?query=${encodeURIComponent(query)}`);
});
