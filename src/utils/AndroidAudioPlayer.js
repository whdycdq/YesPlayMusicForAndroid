import {
  getAndroidPlaybackState,
  pauseAndroidAudio,
  playAndroidAudio,
  seekAndroidAudio,
  setAndroidAudioSource,
  setAndroidAudioVolume,
  stopAndroidAudio,
} from '@/utils/androidMediaControls';

export default class AndroidAudioPlayer {
  constructor(source, volume = 1) {
    this._src = source;
    this._volume = volume;
    this._position = 0;
    this._playing = false;
    this._startedAt = 0;
    this._events = new Map();
    this._sounds = [];

    setAndroidAudioSource(source, volume);
    getAndroidPlaybackState().then(state => {
      if (state?.source === this._src) {
        this.syncNativeState(state);
      }
    });
  }

  _getPosition() {
    if (!this._playing) return this._position;
    return this._position + (Date.now() - this._startedAt) / 1000;
  }

  _setPosition(position) {
    this._position = Math.max(0, Number(position) || 0);
    this._startedAt = Date.now();
  }

  _emit(event, ...args) {
    const listeners = this._events.get(event);
    if (!listeners?.length) return;
    this._events.set(
      event,
      listeners.filter(listener => {
        listener.callback(...args);
        return !listener.once;
      })
    );
  }

  on(event, callback) {
    const listeners = this._events.get(event) || [];
    listeners.push({ callback, once: false });
    this._events.set(event, listeners);
    return this;
  }

  once(event, callback) {
    const listeners = this._events.get(event) || [];
    listeners.push({ callback, once: true });
    this._events.set(event, listeners);
    return this;
  }

  play() {
    if (this._playing) return this;
    this._playing = true;
    this._startedAt = Date.now();
    playAndroidAudio();
    setTimeout(() => this._emit('play'), 0);
    return this;
  }

  pause() {
    if (!this._playing) return this;
    this._setPosition(this._getPosition());
    this._playing = false;
    pauseAndroidAudio();
    return this;
  }

  stop() {
    this._playing = false;
    this._setPosition(0);
    stopAndroidAudio();
    return this;
  }

  playing() {
    return this._playing;
  }

  seek(position) {
    if (position === undefined || position === null) {
      return this._getPosition();
    }
    this._setPosition(position);
    seekAndroidAudio(this._position);
    return this._position;
  }

  volume(value) {
    if (value === undefined) return this._volume;
    this._volume = Math.max(0, Math.min(1, Number(value) || 0));
    setAndroidAudioVolume(this._volume);
    return this;
  }

  fade(_from, to, duration) {
    this.volume(to);
    setTimeout(() => this._emit('fade'), duration);
    return this;
  }

  syncNativeState(state) {
    if (state.source && state.source !== this._src) return;
    if (state.position !== undefined) {
      this._setPosition(state.position);
    }
    if (state.action === 'play' || state.playing === true) {
      const wasPlaying = this._playing;
      this._playing = true;
      this._startedAt = Date.now();
      if (!wasPlaying) setTimeout(() => this._emit('play'), 0);
    } else if (
      state.action === 'pause' ||
      state.action === 'stop' ||
      state.playing === false
    ) {
      this._playing = false;
    }
  }

  emitNativeError(errorCode) {
    this._playing = false;
    this._emit('loaderror', null, Number(errorCode) || 0);
  }
}
