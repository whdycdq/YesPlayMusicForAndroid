<template>
  <div id="app" :class="{ 'user-select-none': userSelectNone }">
    <Scrollbar v-show="!showLyrics" ref="scrollbar" />
    <Navbar v-show="showNavbar" ref="navbar" />
    <main
      ref="main"
      :style="{ overflow: enableScrolling ? 'auto' : 'hidden' }"
      @scroll="handleScroll"
      @touchstart.passive="handleTouchStart"
      @touchmove.passive="handleTouchMove"
      @touchend.passive="handleTouchEnd"
      @touchcancel.passive="handleTouchCancel"
    >
      <div
        class="route-content"
        :class="{
          dragging: routeDragging,
          settling: routeSettling,
        }"
        :style="routeContentStyle"
      >
        <transition :name="routeTransitionName" mode="out-in">
          <keep-alive>
            <router-view
              v-if="$route.meta.keepAlive"
              :key="$route.name"
            ></router-view>
          </keep-alive>
        </transition>
        <transition :name="routeTransitionName" mode="out-in">
          <router-view
            v-if="!$route.meta.keepAlive"
            :key="$route.fullPath"
          ></router-view>
        </transition>
      </div>
    </main>
    <transition name="slide-up">
      <Player v-if="enablePlayer" v-show="showPlayer" ref="player" />
    </transition>
    <Toast />
    <ModalAddTrackToPlaylist v-if="isAccountLoggedIn" />
    <ModalNewPlaylist v-if="isAccountLoggedIn" />
    <transition v-if="enablePlayer" name="slide-up">
      <Lyrics v-show="showLyrics" />
    </transition>
  </div>
</template>

<script>
import ModalAddTrackToPlaylist from './components/ModalAddTrackToPlaylist.vue';
import ModalNewPlaylist from './components/ModalNewPlaylist.vue';
import Scrollbar from './components/Scrollbar.vue';
import Navbar from './components/Navbar.vue';
import Player from './components/Player.vue';
import Toast from './components/Toast.vue';
import { ipcRenderer } from './electron/ipcRenderer';
import { isAccountLoggedIn, isLooseLoggedIn } from '@/utils/auth';
import Lyrics from './views/lyrics.vue';
import { mapState } from 'vuex';

export default {
  name: 'App',
  components: {
    Navbar,
    Player,
    Toast,
    ModalAddTrackToPlaylist,
    ModalNewPlaylist,
    Lyrics,
    Scrollbar,
  },
  data() {
    return {
      isElectron: process.env.IS_ELECTRON, // true || undefined
      userSelectNone: false,
      touchStartX: null,
      touchStartY: null,
      touchStartAt: 0,
      touchStartTabIndex: -1,
      touchAxis: null,
      routeDragX: 0,
      routeDragOpacity: 1,
      routeDragging: false,
      routeSettling: false,
      routeSettleTimer: null,
      routeTransitionName: 'tab-fade',
    };
  },
  computed: {
    ...mapState(['showLyrics', 'settings', 'player', 'enableScrolling']),
    isAccountLoggedIn() {
      return isAccountLoggedIn();
    },
    showPlayer() {
      return (
        [
          'mv',
          'loginUsername',
          'login',
          'loginAccount',
          'lastfmCallback',
        ].includes(this.$route.name) === false
      );
    },
    enablePlayer() {
      return this.player.enabled && this.$route.name !== 'lastfmCallback';
    },
    showNavbar() {
      return this.$route.name !== 'lastfmCallback';
    },
    routeContentStyle() {
      return {
        opacity: this.routeDragOpacity,
        transform: `translate3d(${this.routeDragX}px, 0, 0)`,
      };
    },
  },
  watch: {
    '$route.name'(to, from) {
      const tabs = ['home', 'explore', 'library'];
      const toIndex = tabs.indexOf(to);
      const fromIndex = tabs.indexOf(from);
      if (toIndex < 0 || fromIndex < 0 || toIndex === fromIndex) {
        this.routeTransitionName = 'tab-fade';
      } else {
        this.routeTransitionName =
          toIndex > fromIndex ? 'tab-slide-left' : 'tab-slide-right';
      }
    },
  },
  created() {
    if (this.isElectron) ipcRenderer(this);
    window.addEventListener('keydown', this.handleKeydown);
    this.fetchData();
  },
  methods: {
    handleKeydown(e) {
      if (e.code === 'Space') {
        if (e.target.tagName === 'INPUT') return false;
        if (this.$route.name === 'mv') return false;
        e.preventDefault();
        this.player.playOrPause();
      }
    },
    fetchData() {
      if (!isLooseLoggedIn()) return;
      this.$store.dispatch('fetchLikedSongs');
      this.$store.dispatch('fetchLikedSongsWithDetails');
      this.$store.dispatch('fetchLikedPlaylist');
      if (isAccountLoggedIn()) {
        this.$store.dispatch('fetchLikedAlbums');
        this.$store.dispatch('fetchLikedArtists');
        this.$store.dispatch('fetchLikedMVs');
        this.$store.dispatch('fetchCloudDisk');
      }
    },
    handleScroll() {
      this.$refs.scrollbar.handleScroll();
    },
    handleTouchStart(event) {
      this.clearTouchTracking();
      if (
        process.env.VUE_APP_PLATFORM !== 'android' ||
        this.showLyrics ||
        !['home', 'explore', 'library'].includes(this.$route.name) ||
        event.touches.length !== 1
      ) {
        return;
      }

      const target = event.target;
      if (
        target.closest(
          'input, textarea, select, button, video, audio, [contenteditable], .vue-slider, .plyr'
        )
      ) {
        return;
      }

      const touch = event.touches[0];
      this.touchStartX = touch.clientX;
      this.touchStartY = touch.clientY;
      this.touchStartAt = Date.now();
      this.touchStartTabIndex = ['home', 'explore', 'library'].indexOf(
        this.$route.name
      );
    },
    handleTouchMove(event) {
      if (this.touchStartX === null || event.touches.length !== 1) return;

      const touch = event.touches[0];
      const deltaX = touch.clientX - this.touchStartX;
      const deltaY = touch.clientY - this.touchStartY;
      if (this.touchAxis === null) {
        if (Math.max(Math.abs(deltaX), Math.abs(deltaY)) < 8) return;
        this.touchAxis =
          Math.abs(deltaX) > Math.abs(deltaY) * 1.15
            ? 'horizontal'
            : 'vertical';
      }
      if (this.touchAxis !== 'horizontal') return;

      let visualDeltaX = deltaX;
      if (
        (this.touchStartTabIndex === 0 && deltaX > 0) ||
        (this.touchStartTabIndex === 2 && deltaX < 0)
      ) {
        visualDeltaX *= 0.24;
      }

      const width = Math.max(1, event.currentTarget.clientWidth);
      this.routeDragging = true;
      this.routeSettling = false;
      this.routeDragX = Math.max(-56, Math.min(56, visualDeltaX * 0.22));
      this.routeDragOpacity =
        1 - Math.min(0.12, Math.abs(visualDeltaX) / width / 5);
      this.emitTabSwipe(this.touchStartTabIndex - visualDeltaX / width, true);
    },
    handleTouchEnd(event) {
      if (this.touchStartX === null || event.changedTouches.length !== 1) {
        this.finishTouchInteraction();
        return;
      }

      const touch = event.changedTouches[0];
      const deltaX = touch.clientX - this.touchStartX;
      const deltaY = touch.clientY - this.touchStartY;
      const duration = Date.now() - this.touchStartAt;
      const currentIndex = this.touchStartTabIndex;
      const horizontalGesture = this.touchAxis === 'horizontal';
      this.clearTouchTracking();

      if (
        !horizontalGesture ||
        (Math.abs(deltaX) < 52 &&
          !(Math.abs(deltaX) > 28 && Math.abs(deltaX) / duration > 0.45)) ||
        Math.abs(deltaX) < Math.abs(deltaY) * 1.35 ||
        duration > 1400
      ) {
        this.finishRouteDrag(currentIndex);
        return;
      }

      const tabs = ['home', 'explore', 'library'];
      const nextIndex = currentIndex + (deltaX < 0 ? 1 : -1);
      if (nextIndex >= 0 && nextIndex < tabs.length) {
        this.routeTransitionName =
          nextIndex > currentIndex ? 'tab-slide-left' : 'tab-slide-right';
        this.finishRouteDrag(nextIndex);
        this.$router.push({ name: tabs[nextIndex] });
      } else {
        this.finishRouteDrag(currentIndex);
      }
    },
    handleTouchCancel() {
      const currentIndex = ['home', 'explore', 'library'].indexOf(
        this.$route.name
      );
      this.clearTouchTracking();
      this.finishRouteDrag(currentIndex);
    },
    clearTouchTracking() {
      this.touchStartX = null;
      this.touchStartY = null;
      this.touchStartAt = 0;
      this.touchStartTabIndex = -1;
      this.touchAxis = null;
    },
    emitTabSwipe(position, dragging) {
      window.dispatchEvent(
        new CustomEvent('android-tab-swipe', {
          detail: { position, dragging },
        })
      );
    },
    finishTouchInteraction() {
      const currentIndex = ['home', 'explore', 'library'].indexOf(
        this.$route.name
      );
      this.clearTouchTracking();
      this.finishRouteDrag(currentIndex);
    },
    finishRouteDrag(tabPosition) {
      if (this.routeSettleTimer !== null) {
        window.clearTimeout(this.routeSettleTimer);
      }
      this.routeDragging = false;
      this.routeSettling = true;
      this.routeDragX = 0;
      this.routeDragOpacity = 1;
      if (tabPosition >= 0) this.emitTabSwipe(tabPosition, false);
      this.routeSettleTimer = window.setTimeout(() => {
        this.routeSettling = false;
        this.routeSettleTimer = null;
      }, 260);
    },
  },
};
</script>

<style lang="scss">
#app {
  width: 100%;
  transition: all 0.4s;
}

main {
  position: fixed;
  top: 0;
  bottom: 0;
  right: 0;
  left: 0;
  overflow: auto;
  padding: 64px 10vw 96px 10vw;
  box-sizing: border-box;
  scrollbar-width: none; // firefox
}

@media (max-width: 1336px) {
  main {
    padding: 64px 5vw 96px 5vw;
  }
}

main::-webkit-scrollbar {
  width: 0px;
}

.route-content {
  min-height: 100%;
  transform-origin: center center;
}

.route-content.dragging {
  will-change: transform, opacity;
}

.route-content.settling {
  transition: transform 240ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease-out;
}

.tab-slide-left-enter-active,
.tab-slide-left-leave-active,
.tab-slide-right-enter-active,
.tab-slide-right-leave-active,
.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 150ms ease-out;
}

.tab-slide-left-enter,
.tab-slide-right-leave-to {
  opacity: 0;
  transform: translate3d(24px, 0, 0);
}

.tab-slide-left-leave-to,
.tab-slide-right-enter {
  opacity: 0;
  transform: translate3d(-24px, 0, 0);
}

.tab-fade-enter,
.tab-fade-leave-to {
  opacity: 0;
  transform: translate3d(0, 8px, 0);
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.4s;
}
.slide-up-enter,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
