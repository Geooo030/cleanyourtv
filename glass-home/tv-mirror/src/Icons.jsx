import React from 'react';
export function Icon({ name, size = 20, ...props }) {
  const paths = {
    screen: <><rect x="2" y="3" width="20" height="14" rx="3"/><path d="M8 21h8m-4-4v4"/></>,
    cast: <><path d="M3 11V5a2 2 0 012-2h15a2 2 0 012 2v13a2 2 0 01-2 2h-7M2 15a7 7 0 017 7M2 19a3 3 0 013 3"/><path d="M2 22h.01"/></>,
    play: <path d="M8 5l12 7-12 7z"/>, pause: <><path d="M8 5v14M16 5v14"/></>,
    refresh: <><path d="M20 8a8 8 0 10.1 8M20 3v5h-5"/></>,
    camera: <><path d="M8 5l2-2h4l2 2h4a2 2 0 012 2v12H2V7a2 2 0 012-2z"/><circle cx="12" cy="12" r="4"/></>,
    expand: <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>,
    wifi: <><path d="M3 8a15 15 0 0118 0M6 12a10 10 0 0112 0M9 16a5 5 0 016 0"/><circle cx="12" cy="20" r=".7"/></>,
    arrow: <path d="M5 12h14m-5-5l5 5-5 5"/>, check: <path d="M5 12l4 4L19 6"/>,
    up: <path d="M6 15l6-6 6 6"/>, down: <path d="M6 9l6 6 6-6"/>,
    left: <path d="M15 6l-6 6 6 6"/>, right: <path d="M9 6l6 6-6 6"/>,
    home: <><path d="M3 11l9-8 9 8M5 10v11h5v-7h4v7h5V10"/></>,
    back: <path d="M9 5l-7 7 7 7M2 12h13a6 6 0 016 6"/>,
    menu: <path d="M4 6h16M4 12h16M4 18h16"/>,
    minus: <path d="M5 12h14"/>, plus: <path d="M5 12h14M12 5v14"/>,
    mute: <><path d="M11 4L6 8H2v8h4l5 4zM16 9l6 6M22 9l-6 6"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{paths[name]}</svg>;
}
