// Base URL for file/image assets — relative so it works on any domain via nginx/Vite proxy
export const fileUrl = (path) => `/api/files/${path}`;

// WebSocket base — dynamically picks up the current domain (localhost, cricketplex.com, etc.)
export const WS_URL = `${window.location.origin}/ws`;
