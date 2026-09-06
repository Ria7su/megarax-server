import {
  InteractionContextType,
  SlashCommandBuilder,
  type ChatInputCommandInteraction,
  type Client,
  type Guild,
} from "discord.js";
import type { Env, Manifest } from "./config.js";
import { applyPlan, publishManagedContent } from "./discord/apply.js";
import { permissionReport } from "./discord/permissions.js";
import { rolePanel } from "./discord/roles.js";
import { captureSnapshot } from "./discord/snapshot.js";
import { ticketPanel } from "./discord/tickets.js";
import { previewGuild, summarizePlan } from "./service.js";
import { readPlan, readState, writeSnapshot } from "./storage.js";

export const megaraxCommand = new SlashCommandBuilder()
  .setName("megarax")
  .setDescription("Administra Megarax Network")
  .setDefaultMemberPermissions(0)
  .setContexts(InteractionContextType.Guild)
  .addSubcommand((command) =>
    command.setName("preview").setDescription("Genera un dry-run y snapshot"),
  )
  .addSubcommand((command) =>
    command
      .setName("apply")
      .setDescription("Aplica un plan validado")
      .addStringOption((option) =>
        option.setName("plan").setDescription("ID exacto del plan aprobado").setRequired(true),
      ),
  )
  .addSubcommand((command) =>
    command.setName("status").setDescription("Muestra el estado administrado"),
  )
  .addSubcommand((command) =>
    command.setName("backup").setDescription("Exporta un snapshot del servidor"),
  )
  .addSubcommand((command) =>
    command.setName("repair").setDescription("Detecta deriva y conflictos"),
  )
  .addSubcommand((command) =>
    command.setName("publish").setDescription("Publica contenidos y paneles"),
  )
  .addSubcommand((command) =>
    command.setName("permissions-check").setDescription("Comprueba permisos y jerarquía"),
  );

export async function registerCommands(client: Client, env: Env): Promise<void> {
  const guild = await client.guilds.fetch(env.DISCORD_GUILD_ID);
  await guild.commands.set([megaraxCommand.toJSON()]);
}

export async function handleMegaraxCommand(
  interaction: ChatInputCommandInteraction,
  env: Env,
  manifest: Manifest,
): Promise<void> {
  if (interaction.commandName !== "megarax") return;
  if (interaction.user.id !== env.DISCORD_OWNER_ID) {
    await interaction.reply({
      content: "Este comando está reservado al propietario configurado.",
      ephemeral: true,
    });
    return;
  }
  if (!interaction.guild) return;
  const guild: Guild = interaction.guild;
  const subcommand = interaction.options.getSubcommand();
  await interaction.deferReply({ ephemeral: true });

  if (subcommand === "preview" || subcommand === "repair") {
    const { plan, planFile } = await previewGuild(guild);
    await interaction.editReply(
      `${summarizePlan(plan).slice(0, 3500)}\n\nPlan guardado: \`${planFile}\``,
    );
    return;
  }
  if (subcommand === "apply") {
    const planId = interaction.options.getString("plan", true);
    if (!/^[0-9a-f-]{36}$/i.test(planId)) throw new Error("ID de plan inválido.");
    const plan = await readPlan(`plans/plan-${planId}.json`);
    await applyPlan(guild, manifest, plan);
    await interaction.editReply(`Plan ${planId} aplicado. No se eliminaron recursos desconocidos.`);
    return;
  }
  if (subcommand === "backup") {
    const file = await writeSnapshot(await captureSnapshot(guild));
    await interaction.editReply(`Snapshot creado: \`${file}\``);
    return;
  }
  if (subcommand === "permissions-check") {
    await interaction.editReply(permissionReport(guild, manifest).join("\n"));
    return;
  }
  if (subcommand === "status") {
    const state = await readState(guild.id);
    await interaction.editReply(
      `Recursos administrados: ${Object.keys(state.resources).length}\nÚltima actualización: ${state.updatedAt}`,
    );
    return;
  }
  const published = await publishManagedContent(guild, manifest);
  const state = await readState(guild.id);
  const ticketsId = state.resources["channel:ticket_create"]?.id;
  const rolesId = state.resources["channel:role_select"]?.id;
  const ticketChannel = ticketsId ? guild.channels.cache.get(ticketsId) : undefined;
  const roleChannel = rolesId ? guild.channels.cache.get(rolesId) : undefined;
  if (ticketChannel?.isTextBased() && !ticketChannel.isThread())
    await ticketChannel.send(ticketPanel());
  if (roleChannel?.isTextBased() && !roleChannel.isThread())
    await roleChannel.send(rolePanel(manifest));
  await interaction.editReply(`Publicados ${published} contenidos y paneles configurados.`);
}
