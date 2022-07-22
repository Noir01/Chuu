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
//public class Uniqueness {
//    @ClassRule
//    public static final TestRule res = TestResources.INSTANCE;
//
//    @Property
//    public void onlyOneUser(@From(UniqueGen.class) ArtistPlays artistPlays) {
//
//// TODO
//        WrapperReturnNowPlaying wrapperReturnNowPlaying = TestResources.dao
//                .whoKnows(1L, TestResources.channelWorker.getGuild()
//                        .getIdLong(), Integer.MAX_VALUE);
//
//        System.out.println(artistPlays.getArtistName());
//        List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
//        Assert.assertEquals(returnNowPlayings.get(0).getLastFMId(), "pablopita");
//        long count = returnNowPlayings.stream().filter(x -> !x.getLastFMId().equals("pablopita") && x.getPlayNumber() > 2).count();
//        Assert.assertEquals(0L, count);
//
//    }
//
//
//}
