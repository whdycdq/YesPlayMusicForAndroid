import { registerPlugin } from '@capacitor/core';
import { isAndroidApp } from '@/utils/androidPlatform';

const AndroidMediaControls = registerPlugin('AndroidMediaControls');
let initialized = false;

function safely(promise) {
  if (promise && typeof promise.catch === 'function') {
    promise.catch(error => console.debug('[AndroidMediaControls]', error));
  }
}

export function initAndroidMediaControls(player) {
  if (!isAndroidApp || initialized) return;
  initialized = true;

  safely(
    AndroidMediaControls.addListener('mediaAction', event => {
      player._howler?.syncNativeState?.(event);
      switch (event.action) {
        case 'play':
          player._setPlaying(true);
          break;
        case 'pause':
          player.pause(true);
          break;
        case 'previous':
          player.playPrevTrack();
          break;
        case 'next':
          player.playNextTrack();
          break;
        case 'stop':
          player.pause(true);
          break;
        case 'seek':
          break;
        case 'ended':
          player._nextTrackCallback();
          break;
        case 'error':
          player._howler?.emitNativeError?.(event.errorCode);
          break;
        default:
          break;
      }
    })
  );
}

export function setAndroidAudioSource(source, volume) {
  if (!isAndroidApp) return;
  safely(
    AndroidMediaControls.setSource({
      source,
      volume: Number(volume) || 0,
    })
  );
}

export function playAndroidAudio() {
  if (!isAndroidApp) return;
  safely(AndroidMediaControls.play());
}

export function pauseAndroidAudio() {
  if (!isAndroidApp) return;
  safely(AndroidMediaControls.pause());
}

export function stopAndroidAudio() {
  if (!isAndroidApp) return;
  safely(AndroidMediaControls.stop());
}

export function seekAndroidAudio(position) {
  if (!isAndroidApp) return;
  safely(
    AndroidMediaControls.seek({
      position: Number(position) || 0,
    })
  );
}

export function setAndroidAudioVolume(volume) {
  if (!isAndroidApp) return;
  safely(
    AndroidMediaControls.setVolume({
      volume: Number(volume) || 0,
    })
  );
}

export function getAndroidPlaybackState() {
  if (!isAndroidApp) return Promise.resolve(null);
  return AndroidMediaControls.getState().catch(error => {
    console.debug('[AndroidMediaControls]', error);
    return null;
  });
}

export function updateAndroidMediaMetadata(track, duration) {
  if (!isAndroidApp || !track) return;
  safely(
    AndroidMediaControls.setMetadata({
      title: track.name || '',
      artist: (track.ar || []).map(artist => artist.name).join(', '),
      album: track.al?.name || '',
      artwork: track.al?.picUrl
        ? `${track.al.picUrl.replace(/^http:/, 'https:')}?param=512y512`
        : '',
      duration: Number(duration) || 0,
    })
  );
}

export function updateAndroidPlaybackState(playing, position, duration) {
  if (!isAndroidApp) return;
  safely(
    AndroidMediaControls.setPlaybackState({
      playing: Boolean(playing),
      position: Number(position) || 0,
      duration: Number(duration) || 0,
    })
  );
}
