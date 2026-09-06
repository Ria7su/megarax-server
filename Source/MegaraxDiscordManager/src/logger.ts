import { mkdirSync } from "node:fs";
import pino from "pino";

mkdirSync("logs", { recursive: true });

export const logger = pino(
  {
    name: "megarax-discord-manager",
    level: process.env.NODE_ENV === "production" ? "info" : "debug",
    redact: {
      paths: ["token", "*.token", "DISCORD_BOT_TOKEN", "authorization", "*.authorization"],
      censor: "[REDACTED]",
    },
  },
  pino.destination({ dest: "logs/manager.log", sync: false, mkdir: true }),
);
