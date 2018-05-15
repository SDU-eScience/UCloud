package dk.sdu.cloud;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.xpack.client.PreBuiltXPackTransportClient;
import org.elasticsearch.xpack.client.PreBuiltXPackTransportClient;


public class GenDataCiteElasticIndex {
    static Utils utils = new Utils();


    public static void main(String[] args) throws IOException {
        TransportClient client = new PreBuiltXPackTransportClient(Settings.builder()
                .put("cluster.name", "sducloud-elastic")
                .put("xpack.security.user", "transport_client_user:x-pack-test-password").build())
                .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300))
                .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9301));
        utils.readCephFileList();


        System.err.println(client.connectedNodes().size());
        System.err.println(client.admin().indices());

        //queryByScroll(client);



    }

//    public ElasticsearchClient(final String[] addresses, final String clusterName) {
//        // create default settings and add cluster name
//        Settings.Builder settings = Settings.builder()
//                .put("cluster.routing.allocation.enable", "all")
//                .put("cluster.routing.allocation.allow_rebalance", "always");
//        if (clusterName != null) settings.put("cluster.name", clusterName);
//
//        // create a client
//        TransportClient tc = new PreBuiltTransportClient(settings.build());
//
//        for (String address: addresses) {
//            String a = address.trim();
//            int p = a.indexOf(':');
//            if (p >= 0) try {
//                InetAddress i = InetAddress.getByName(a.substring(0, p));
//                int port = Integer.parseInt(a.substring(p + 1));
//                tc.addTransportAddress(new InetSocketTransportAddress(i, port));
//            } catch (UnknownHostException e) {
//                Data.logger.warn("", e);
//            }
//        }
//        this.elasticsearchClient = tc;
//    }

//    private static void queryByScroll(TransportClient transportClient) throws IOException {
//
//
//        SearchResponse searchResponse = transportClient.prepareSearch("product_index").setTypes("product")
//                .setQuery(QueryBuilders.termQuery("product_name", "飞利浦"))
//                .setScroll(new TimeValue(60000))
//                .setSize(3)
//                .get();
//
//        int count = 0;
//
//        do {
//            for (SearchHit searchHit : searchResponse.getHits().getHits()) {
//
//                System.err.println("count=" + ++count);
//                System.err.println(searchHit.getSourceAsString());
//            }
//
//            searchResponse = transportClient.prepareSearchScroll(searchResponse.getScrollId()).setScroll(new TimeValue(60000))
//                    .execute()
//                    .actionGet();
//        } while (searchResponse.getHits().getHits().length != 0);
//    }
}
