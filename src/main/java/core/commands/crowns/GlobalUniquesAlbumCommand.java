package core.commands.crowns;

import core.commands.utils.CommandCategory;
import dao.ChuuService;
import dao.entities.AlbumPlays;
import dao.entities.UniqueWrapper;

import java.util.Arrays;
import java.util.List;

public class GlobalUniquesAlbumCommand extends UniqueAlbumCommand {
    public GlobalUniquesAlbumCommand(ChuuService dao) {
        super(dao);
        this.respondInPrivate = true;
        isLongRunningCommand = true;

    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.USER_STATS;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public UniqueWrapper<AlbumPlays> getList(long ignored, String lastFmName) {
        return db.getGlobalAlbumUniques(lastFmName);
    }

    @Override
    public String getDescription() {
        return "Your unique albums considering all bot users";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("globalalbumunique", "galbu");
    }

    @Override
    public String getName() {
        return "Global album uniques";
    }
}
