<template>
  <div ref="contextMenu" class="context-menu">
    <div
      v-if="showMenu"
      ref="menu"
      class="menu"
      tabindex="-1"
      :style="{ top: top, left: left }"
      @blur="closeMenu"
      @click="closeMenu"
    >
      <slot></slot>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';

export default {
  name: 'ContextMenu',
  data() {
    return {
      showMenu: false,
      top: '0px',
      left: '0px',
    };
  },
  computed: {
    ...mapState(['player']),
  },
  methods: {
    setMenu(top, left) {
      const menu = this.$refs.menu;
      const isAndroid =
        document.body.getAttribute('data-platform') === 'android';
      const nav = isAndroid ? document.querySelector('nav') : null;
      const player = document.querySelector('.player');
      const playerVisible =
        player && getComputedStyle(player).display !== 'none';
      const topBoundary = nav ? nav.getBoundingClientRect().bottom + 8 : 8;
      const bottomBoundary = playerVisible
        ? player.getBoundingClientRect().top - 8
        : window.innerHeight - 8;
      const availableHeight = Math.max(160, bottomBoundary - topBoundary);

      menu.style.maxHeight = `${availableHeight}px`;
      const menuHeight = menu.getBoundingClientRect().height;
      const menuWidth = menu.getBoundingClientRect().width;
      const largestTop = Math.max(topBoundary, bottomBoundary - menuHeight);
      const largestLeft = Math.max(8, window.innerWidth - menuWidth - 8);
      top = Math.max(topBoundary, Math.min(top, largestTop));
      left = Math.max(8, Math.min(left, largestLeft));

      let fixedContainingBlock = menu.parentElement;
      while (fixedContainingBlock && fixedContainingBlock !== document.body) {
        const style = getComputedStyle(fixedContainingBlock);
        if (
          style.transform !== 'none' ||
          style.perspective !== 'none' ||
          style.filter !== 'none'
        ) {
          break;
        }
        fixedContainingBlock = fixedContainingBlock.parentElement;
      }
      const containingRect =
        fixedContainingBlock && fixedContainingBlock !== document.body
          ? fixedContainingBlock.getBoundingClientRect()
          : { top: 0, left: 0 };
      this.top = top - containingRect.top + 'px';
      this.left = left - containingRect.left + 'px';
    },

    closeMenu() {
      this.showMenu = false;
      if (this.$parent.closeMenu !== undefined) {
        this.$parent.closeMenu();
      }
      this.$store.commit('enableScrolling', true);
    },

    openMenu(e) {
      this.showMenu = true;
      this.$nextTick(
        function () {
          this.$refs.menu.focus();
          this.setMenu(e.y, e.x);
        }.bind(this)
      );
      e.preventDefault();
      this.$store.commit('enableScrolling', false);
    },
  },
};
</script>

<style lang="scss" scoped>
.context-menu {
  width: 100%;
  height: 100%;
  user-select: none;
}

.menu {
  position: fixed;
  min-width: 136px;
  max-width: 240px;
  list-style: none;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 6px 12px -4px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(12px);
  border-radius: 12px;
  box-sizing: border-box;
  padding: 6px;
  z-index: 1000;
  -webkit-app-region: no-drag;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  transition: background 125ms ease-out, opacity 125ms ease-out,
    transform 125ms ease-out;

  &:focus {
    outline: none;
  }
}

[data-theme='dark'] {
  .menu {
    background: rgba(36, 36, 36, 0.78);
    backdrop-filter: blur(16px) contrast(120%) brightness(60%);
    border: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: 0 0 6px rgba(255, 255, 255, 0.08);
  }
  .menu .item:hover {
    color: var(--color-text);
  }
}

@supports (-moz-appearance: none) {
  .menu {
    background-color: var(--color-body-bg) !important;
  }
}

.menu .item {
  font-weight: 600;
  font-size: 14px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: default;
  color: var(--color-text);
  display: flex;
  align-items: center;
  &:hover {
    color: var(--color-primary);
    background: var(--color-primary-bg-for-transparent);
    transition: opacity 125ms ease-out, transform 125ms ease-out;
  }
  &:active {
    opacity: 0.75;
    transform: scale(0.95);
  }

  .svg-icon {
    height: 16px;
    width: 16px;
    margin-right: 5px;
  }
}

hr {
  margin: 4px 10px;
  background: rgba(128, 128, 128, 0.18);
  height: 1px;
  box-shadow: none;
  border: none;
}

.item-info {
  padding: 10px 10px;
  display: flex;
  align-items: center;
  color: var(--color-text);
  cursor: default;
  img {
    height: 38px;
    width: 38px;
    border-radius: 4px;
  }
  .info {
    margin-left: 10px;
  }
  .title {
    font-size: 16px;
    font-weight: 600;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 1;
    overflow: hidden;
    word-break: break-all;
  }
  .subtitle {
    font-size: 12px;
    opacity: 0.68;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 1;
    overflow: hidden;
    word-break: break-all;
  }
}
</style>
