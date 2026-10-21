export const REMOTE_KEYS = Object.freeze({
  UP: 19, DOWN: 20, LEFT: 21, RIGHT: 22, OK: 23,
  HOME: 3, BACK: 4, MENU: 82,
  VOLUME_UP: 24, VOLUME_DOWN: 25, MUTE: 164,
});

export function resolveRemoteKey(key) {
  if (typeof key !== 'string' || !Object.hasOwn(REMOTE_KEYS, key)) throw new Error('不支持的遥控按键');
  return REMOTE_KEYS[key];
}
