package core.apis.youtube;


import org.junit.jupiter.api.Test;

public class InvidousSearchTest {

    @Test
    public void doSearch() {
        InvidousSearch invidousSearch = new InvidousSearch();
        String s = invidousSearch.doSearch("counterparts you are not you anymore");
    }
}
