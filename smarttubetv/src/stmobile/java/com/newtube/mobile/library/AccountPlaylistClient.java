package com.newtube.mobile.library;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;

/** The picker’s only account API boundary; no extractor or account storage logic is duplicated. */
interface AccountPlaylistClient {
    boolean signedIn();
    Observable<List<PlaylistInfo>> playlists(String videoId);
    Observable<Void> membership(String playlistId, String videoId, boolean add);
    void changed();

    static AccountPlaylistClient existing(Context context) {
        Context app = context.getApplicationContext();
        return new AccountPlaylistClient() {
            @Override public boolean signedIn() { return YouTubeSignInService.instance().isSigned(); }
            @Override public Observable<List<PlaylistInfo>> playlists(String id) {
                return YouTubeMediaItemService.instance().getPlaylistsInfoObserve(id);
            }
            @Override public Observable<Void> membership(String playlist, String video, boolean add) {
                return add ? YouTubeMediaItemService.instance().addToPlaylistObserve(playlist, video)
                        : YouTubeMediaItemService.instance().removeFromPlaylistObserve(playlist, video);
            }
            @Override public void changed() { BrowsePresenter.instance(app).onPlaylistEdited(); }
        };
    }
}
