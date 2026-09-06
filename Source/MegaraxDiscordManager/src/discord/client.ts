import { Client, GatewayIntentBits, Partials } from "discord.js";
import type { Env } from "../config.js";

export function createClient(): Client {
  return new Client({
    intents: [
      GatewayIntentBits.Guilds,
      GatewayIntentBits.GuildMembers,
      GatewayIntentBits.GuildMessages,
      GatewayIntentBits.MessageContent,
    ],
    partials: [Partials.Channel, Partials.Message],
  });
}

export async function loginAndFetchGuild(client: Client, env: Env) {
  await client.login(env.DISCORD_BOT_TOKEN);
  return client.guilds.fetch(env.DISCORD_GUILD_ID);
}
