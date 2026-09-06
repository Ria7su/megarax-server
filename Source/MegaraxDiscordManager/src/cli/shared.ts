import type { Client, Guild } from "discord.js";
import { loadEnv, loadManifest } from "../config.js";
import { createClient, loginAndFetchGuild } from "../discord/client.js";

export async function withGuild<T>(work: Parameters<typeof runWithGuild<T>>[0]): Promise<T> {
  return runWithGuild(work);
}

async function runWithGuild<T>(
  work: (context: {
    client: Client;
    guild: Guild;
    env: ReturnType<typeof loadEnv>;
    manifest: Awaited<ReturnType<typeof loadManifest>>;
  }) => Promise<T>,
): Promise<T> {
  const env = loadEnv();
  const manifest = await loadManifest();
  const client = createClient();
  try {
    const guild = await loginAndFetchGuild(client, env);
    return await work({ client, guild, env, manifest });
  } finally {
    void client.destroy();
  }
}
