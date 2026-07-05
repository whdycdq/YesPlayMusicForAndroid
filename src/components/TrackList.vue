<template>
  <div class="track-list">
    <ContextMenu ref="menu">
      <div v-show="type !== 'cloudDisk'" class="item-info">
        <img
          :src="rightClickedTrackComputed.al.picUrl | resizeImage(224)"
          loading="lazy"
        />
        <div class="info">
          <div class="title">{{ rightClickedTrackComputed.name }}</div>
          <div class="subtitle">{{ rightClickedTrackComputed.ar[0].name }}</div>
        </div>
      </div>
      <hr v-show="type !== 'cloudDisk'" />
      <div class="item" @click="play">{{ $t('contextMenu.play') }}</div>
      <div class="item" @click="addToQueue">{{
        $t('contextMenu.addToQueue')
      }}</div>
      <div
        v-if="extraContextMenuItem.includes('removeTrackFromQueue')"
        class="item"
        @click="removeTrackFromQueue"
        >从队列删除</div
      >
      <hr v-show="type !== 'cloudDisk'" />
      <div
        v-show="!isRightClickedTrackLiked && type !== 'cloudDisk'"
        class="item"
        @click="like"
      >
        {{ $t('contextMenu.saveToMyLikedSongs') }}
      </div>
      <div
        v-show="isRightClickedTrackLiked && type !== 'cloudDisk'"
        class="item"
        @click="like"
      >
        {{ $t('contextMenu.removeFromMyLikedSongs') }}
      </div>
      <div
        v-if="extraContextMenuItem.includes('removeTrackFromPlaylist')"
        class="item"
        @click="removeTrackFromPlaylist"
        >从歌单中删除</div
      >
      <div
        v-show="type !== 'cloudDisk'"
        class="item"
        @click="addTrackToPlaylist"
        >{{ $t('contextMenu.addToPlaylist') }}</div
      >
      <div v-show="type !== 'cloudDisk'" class="item" @click="copyLink">{{
        $t('contextMenu.copyUrl')
      }}</div>
      <div
        v-if="extraContextMenuItem.includes('removeTrackFromCloudDisk')"
        class="item"
        @click="removeTrackFromCloudDisk"
        >从云盘中删除</div
      >
      <template v-if="androidContextMenu">
        <hr v-if="rightClickedArtists.length > 0 || rightClickedAlbum.id" />
        <div
          v-for="artist in rightClickedArtists"
          :key="`artist-${artist.id}`"
          class="item"
          @click="goToArtist(artist.id)"
        >
          查看歌手：{{ artist.name }}
        </div>
        <div
          v-if="rightClickedAlbum.id"
          class="item"
          @click="goToAlbum(rightClickedAlbum.id)"
        >
          查看专辑：{{ rightClickedAlbum.name }}
        </div>
      </template>
    </ContextMenu>

    <div :style="listStyles">
      <TrackListItem
        v-for="(track, index) in tracks"
        :key="itemKey === 'id' ? track.id : `${track.id}${index}`"
        :track-prop="track"
        :track-no="index + 1"
        :highlight-playing-track="highlightPlayingTrack"
        @touchstart.native="startTrackLongPress($event, track, index)"
        @touchmove.native="moveTrackLongPress($event)"
        @touchend.native="endTrackLongPress"
        @touchcancel.native="cancelTrackLongPress"
        @click.capture.native="
          playThisListOnAndroid($event, track.id || track.songId, track)
        "
        @dblclick.native="
          playThisListOnDesktop(track.id || track.songId, track)
        "
        @contextmenu.native="handleContextMenu($event, track, index)"
      />
    </div>
  </div>
</template>

<script>
import { mapActions, mapMutations, mapState } from 'vuex';
import { addOrRemoveTrackFromPlaylist } from '@/api/playlist';
import { cloudDiskTrackDelete } from '@/api/user';
import { isAccountLoggedIn } from '@/utils/auth';

import TrackListItem from '@/components/TrackListItem.vue';
import ContextMenu from '@/components/ContextMenu.vue';
import locale from '@/locale';

export default {
  name: 'TrackList',
  components: {
    TrackListItem,
    ContextMenu,
  },
  props: {
    tracks: {
      type: Array,
      default: () => {
        return [];
      },
    },
    type: {
      type: String,
      default: 'tracklist',
    }, // tracklist | album | playlist | cloudDisk
    id: {
      type: Number,
      default: 0,
    },
    dbclickTrackFunc: {
      type: String,
      default: 'default',
    },
    albumObject: {
      type: Object,
      default: () => {
        return {
          artist: {
            name: '',
          },
        };
      },
    },
    extraContextMenuItem: {
      type: Array,
      default: () => {
        return [
          // 'removeTrackFromPlaylist'
          // 'removeTrackFromQueue'
          // 'removeTrackFromCloudDisk'
        ];
      },
    },
    columnNumber: {
      type: Number,
      default: 4,
    },
    highlightPlayingTrack: {
      type: Boolean,
      default: true,
    },
    itemKey: {
      type: String,
      default: 'id',
    },
    trackIds: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      rightClickedTrack: {
        id: 0,
        name: '',
        ar: [{ name: '' }],
        al: { picUrl: '' },
      },
      rightClickedTrackIndex: -1,
      listStyles: {},
      androidContextMenu: process.env.VUE_APP_PLATFORM === 'android',
      longPressTimer: null,
      longPressStartX: 0,
      longPressStartY: 0,
      longPressTrack: null,
      longPressTrackIndex: -1,
      ignoreClickUntil: 0,
    };
  },
  computed: {
    ...mapState(['liked', 'player']),
    isRightClickedTrackLiked() {
      return this.liked.songs.includes(this.rightClickedTrack?.id);
    },
    rightClickedTrackComputed() {
      return this.type === 'cloudDisk'
        ? {
            id: 0,
            name: '',
            ar: [{ name: '' }],
            al: { picUrl: '' },
          }
        : this.rightClickedTrack;
    },
    rightClickedPlayableTrack() {
      return this.type === 'cloudDisk'
        ? this.rightClickedTrack?.simpleSong || {}
        : this.rightClickedTrack || {};
    },
    rightClickedArtists() {
      return (
        this.rightClickedPlayableTrack.ar ||
        this.rightClickedPlayableTrack.artists ||
        []
      ).filter(artist => artist?.id);
    },
    rightClickedAlbum() {
      return (
        this.rightClickedPlayableTrack.al ||
        this.rightClickedPlayableTrack.album ||
        {}
      );
    },
  },
  created() {
    if (this.type === 'tracklist') {
      this.listStyles = {
        display: 'grid',
        gap: '4px',
        gridTemplateColumns: `repeat(${this.columnNumber}, 1fr)`,
      };
    }
  },
  beforeDestroy() {
    this.cancelTrackLongPress();
  },
  methods: {
    ...mapMutations(['updateModal']),
    ...mapActions(['nextTrack', 'showToast', 'likeATrack']),
    isAndroid() {
      return document.body.getAttribute('data-platform') === 'android';
    },
    normalizeTrack(track) {
      return this.type === 'cloudDisk' ? track.simpleSong : track;
    },
    getTrackIDs() {
      const source = this.trackIds.length > 0 ? this.trackIds : this.tracks;
      return source.map(track =>
        typeof track === 'object' ? track.id || track.songId : track
      );
    },
    playThisListOnAndroid(event, trackID, track) {
      if (document.body.getAttribute('data-platform') !== 'android') return;
      if (event.target.closest?.('button')) return;

      event.preventDefault();
      event.stopPropagation();
      if (Date.now() < this.ignoreClickUntil) return;

      this.playThisList(trackID, this.normalizeTrack(track));
    },
    playThisListOnDesktop(trackID, track) {
      if (document.body.getAttribute('data-platform') === 'android') return;
      this.playThisList(trackID, this.normalizeTrack(track));
    },
    startTrackLongPress(event, track, index) {
      if (!this.isAndroid() || event.touches.length !== 1) return;
      if (event.target.closest?.('button')) return;

      this.cancelTrackLongPress();
      const touch = event.touches[0];
      this.longPressStartX = touch.clientX;
      this.longPressStartY = touch.clientY;
      this.longPressTrack = track;
      this.longPressTrackIndex = index;
      this.longPressTimer = window.setTimeout(() => {
        this.ignoreClickUntil = Date.now() + 800;
        navigator.vibrate?.(18);
        this.openMenu(
          {
            x: this.longPressStartX,
            y: this.longPressStartY,
            preventDefault() {},
          },
          this.longPressTrack,
          this.longPressTrackIndex
        );
        this.longPressTimer = null;
      }, 520);
    },
    moveTrackLongPress(event) {
      if (!this.longPressTimer || event.touches.length !== 1) return;
      const touch = event.touches[0];
      if (
        Math.abs(touch.clientX - this.longPressStartX) > 12 ||
        Math.abs(touch.clientY - this.longPressStartY) > 12
      ) {
        this.cancelTrackLongPress();
      }
    },
    endTrackLongPress() {
      this.cancelTrackLongPress();
    },
    cancelTrackLongPress() {
      if (this.longPressTimer !== null) {
        window.clearTimeout(this.longPressTimer);
      }
      this.longPressTimer = null;
    },
    goToArtist(id) {
      this.$router.push(`/artist/${id}`);
    },
    goToAlbum(id) {
      this.$router.push(`/album/${id}`);
    },
    handleContextMenu(event, track, index) {
      if (this.isAndroid()) {
        event.preventDefault();
        return;
      }
      this.openMenu(event, track, index);
    },
    openMenu(e, track, index = -1) {
      this.rightClickedTrack = track;
      this.rightClickedTrackIndex = index;
      this.$refs.menu.openMenu(e);
    },
    closeMenu() {
      this.rightClickedTrack = {
        id: 0,
        name: '',
        ar: [{ name: '' }],
        al: { picUrl: '' },
      };
      this.rightClickedTrackIndex = -1;
    },
    playThisList(trackID, track) {
      if (this.dbclickTrackFunc === 'default') {
        this.playThisListDefault(trackID, track);
      } else if (this.dbclickTrackFunc === 'none') {
        // do nothing
      } else if (this.dbclickTrackFunc === 'playTrackOnListByID') {
        this.player.playTrackOnListByID(trackID, 'default', track);
      } else if (this.dbclickTrackFunc === 'playPlaylistByID') {
        this.player.replacePlaylist(
          this.getTrackIDs(),
          this.id,
          'playlist',
          trackID,
          track
        );
      } else if (this.dbclickTrackFunc === 'playAList') {
        this.player.replacePlaylist(
          this.getTrackIDs(),
          this.id,
          'artist',
          trackID,
          track
        );
      } else if (this.dbclickTrackFunc === 'dailyTracks') {
        this.player.replacePlaylist(
          this.getTrackIDs(),
          '/daily/songs',
          'url',
          trackID,
          track
        );
      } else if (this.dbclickTrackFunc === 'playCloudDisk') {
        this.player.replacePlaylist(
          this.getTrackIDs(),
          this.id,
          'cloudDisk',
          trackID,
          track
        );
      }
    },
    playThisListDefault(trackID, track) {
      const trackIDs = this.getTrackIDs();
      if (this.type === 'playlist') {
        this.player.replacePlaylist(
          trackIDs,
          this.id,
          'playlist',
          trackID,
          track
        );
      } else if (this.type === 'album') {
        this.player.replacePlaylist(trackIDs, this.id, 'album', trackID, track);
      } else if (this.type === 'tracklist') {
        this.player.replacePlaylist(
          trackIDs,
          this.id,
          'artist',
          trackID,
          track
        );
      }
    },
    play() {
      this.player.addTrackToPlayNext(this.rightClickedTrack.id, true);
    },
    addToQueue() {
      this.player.addTrackToPlayNext(this.rightClickedTrack.id);
    },
    like() {
      this.likeATrack(this.rightClickedTrack.id);
    },
    addTrackToPlaylist() {
      if (!isAccountLoggedIn()) {
        this.showToast(locale.t('toast.needToLogin'));
        return;
      }
      this.updateModal({
        modalName: 'addTrackToPlaylistModal',
        key: 'show',
        value: true,
      });
      this.updateModal({
        modalName: 'addTrackToPlaylistModal',
        key: 'selectedTrackID',
        value: this.rightClickedTrack.id,
      });
    },
    removeTrackFromPlaylist() {
      if (!isAccountLoggedIn()) {
        this.showToast(locale.t('toast.needToLogin'));
        return;
      }
      if (confirm(`确定要从歌单删除 ${this.rightClickedTrack.name}？`)) {
        let trackID = this.rightClickedTrack.id;
        addOrRemoveTrackFromPlaylist({
          op: 'del',
          pid: this.id,
          tracks: trackID,
        }).then(data => {
          this.showToast(
            data.body.code === 200
              ? locale.t('toast.removedFromPlaylist')
              : data.body.message
          );
          this.$parent.removeTrack(trackID);
        });
      }
    },
    copyLink() {
      this.$copyText(
        `https://music.163.com/song?id=${this.rightClickedTrack.id}`
      )
        .then(() => {
          this.showToast(locale.t('toast.copied'));
        })
        .catch(err => {
          this.showToast(`${locale.t('toast.copyFailed')}${err}`);
        });
    },
    removeTrackFromQueue() {
      this.$store.state.player.removeTrackFromQueue(
        this.rightClickedTrackIndex
      );
    },
    removeTrackFromCloudDisk() {
      if (confirm(`确定要从云盘删除 ${this.rightClickedTrack.songName}？`)) {
        let trackID = this.rightClickedTrack.songId;
        cloudDiskTrackDelete(trackID).then(data => {
          this.showToast(
            data.code === 200 ? '已将此歌曲从云盘删除' : data.message
          );
          let newCloudDisk = this.liked.cloudDisk.filter(
            t => t.songId !== trackID
          );
          this.$store.commit('updateLikedXXX', {
            name: 'cloudDisk',
            data: newCloudDisk,
          });
        });
      }
    },
  },
};
</script>

<style lang="scss" scoped></style>
