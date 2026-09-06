import { Events } from "discord.js";
import { handleMegaraxCommand } from "./commands.js";
import { loadEnv, loadManifest } from "./config.js";
import { createClient } from "./discord/client.js";
import { handleRoleButton } from "./discord/roles.js";
import { handleTicketButton } from "./discord/tickets.js";
import { logger } from "./logger.js";

const env = loadEnv();
const manifest = await loadManifest();
const client = createClient();

process.on("unhandledRejection", (error) => logger.error({ error }, "Unhandled rejection"));
process.on("uncaughtException", (error) => {
  logger.fatal({ error }, "Uncaught exception");
  process.exitCode = 1;
});

client.once(Events.ClientReady, (ready) => {
  logger.info({ user: ready.user.tag, guildId: env.DISCORD_GUILD_ID }, "Bot ready");
});

client.on(Events.InteractionCreate, async (interaction) => {
  try {
    if (interaction.isChatInputCommand()) await handleMegaraxCommand(interaction, env, manifest);
    if (interaction.isButton()) {
      if (await handleTicketButton(interaction, manifest)) return;
      await handleRoleButton(interaction, manifest);
    }
  } catch (error) {
    logger.error({ error, interactionId: interaction.id }, "Interaction failed");
    const message =
      "La operación falló de forma segura. Revisa los logs y vuelve a generar un dry-run.";
    if (interaction.isRepliable()) {
      if (interaction.deferred || interaction.replied) await interaction.editReply(message);
      else await interaction.reply({ content: message, ephemeral: true });
    }
  }
});

await client.login(env.DISCORD_BOT_TOKEN);
