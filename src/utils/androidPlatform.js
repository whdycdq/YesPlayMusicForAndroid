import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';

export const isAndroidBuild = process.env.VUE_APP_PLATFORM === 'android';
export const isAndroidApp =
  isAndroidBuild && Capacitor.getPlatform() === 'android';

export function initAndroidPlatform(router, store) {
  if (!isAndroidBuild) return;

  document.documentElement.classList.add('android-app');
  document.body.setAttribute('data-platform', 'android');

  if (!isAndroidApp) return;

  const syncStatusBarInset = async () => {
    try {
      const info = await StatusBar.getInfo();
      document.documentElement.style.setProperty(
        '--android-status-bar-height',
        `${Math.max(0, Number(info.height) || 0)}px`
      );
    } catch (_) {
      document.documentElement.style.setProperty(
        '--android-status-bar-height',
        '24px'
      );
    }
  };

  const syncStatusBarTheme = () => {
    const dark = document.body.getAttribute('data-theme') === 'dark';
    StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light }).catch(
      () => {}
    );
  };

  StatusBar.setOverlaysWebView({ overlay: true })
    .then(syncStatusBarInset)
    .catch(syncStatusBarInset);
  syncStatusBarTheme();
  new MutationObserver(syncStatusBarTheme).observe(document.body, {
    attributes: true,
    attributeFilter: ['data-theme'],
  });
  window.addEventListener('resize', syncStatusBarInset);

  CapacitorApp.addListener('backButton', () => {
    if (document.body.hasAttribute('data-mobile-search-open')) {
      window.dispatchEvent(new CustomEvent('android-close-search'));
      return;
    }

    if (store.state.showLyrics) {
      store.commit('toggleLyrics');
      return;
    }

    if (router.currentRoute.path !== '/') {
      router.back();
      return;
    }

    CapacitorApp.exitApp();
  });
}
