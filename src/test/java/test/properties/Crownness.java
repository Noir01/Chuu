//package test.properties;
//
//import com.pholser.junit.quickcheck.From;
//import com.pholser.junit.quickcheck.Property;
//import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
//import dao.entities.ArtistPlays;
//import dao.entities.ReturnNowPlaying;
//import dao.entities.WrapperReturnNowPlaying;
//import org.junit.Assert;
//import org.junit.ClassRule;
//import org.junit.rules.TestRule;
//import org.junit.runner.RunWith;
//import test.commands.utils.TestResources;
//
//import java.util.List;
//
//@RunWith(JUnitQuickcheck.class)
//public class Crownness {
//    @ClassRule
//
//    public static final TestRule res = TestResources.INSTANCE;
//
//    @Property
//    public void youAreFirst(@From(CrownGen.class) ArtistPlays artistPlays) {
//
//        //Who knows the given artist
//        // TODO long
//        WrapperReturnNowPlaying wrapperReturnNowPlaying = TestResources.dao
//                .whoKnows(1L, TestResources.channelWorker.getGuild()
//                        .getIdLong(), Integer.MAX_VALUE);
//
//        System.out.println(artistPlays.getArtistName());
//
//        List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
//        //We know there is at least one
//        Assert.assertTrue(returnNowPlayings.size() >= 1);
//        //we should be the first one
//        Assert.assertEquals(returnNowPlayings.get(0).getLastFMId(), "pablopita");
//        int plays = returnNowPlayings.get(0).getPlayNumber();
//
//        for (ReturnNowPlaying returnNowPlaying : returnNowPlayings) {
//            Assert.assertTrue(returnNowPlaying.getPlayNumber() <= plays);
//        }
//
//    }
//}
