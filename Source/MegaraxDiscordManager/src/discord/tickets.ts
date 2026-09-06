import { mkdir, writeFile } from "node:fs/promises";
import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  ChannelType,
  EmbedBuilder,
  PermissionFlagsBits,
  type ButtonInteraction,
  type GuildMember,
  type TextChannel,
} from "discord.js";
import type { Manifest } from "../config.js";
import { readState } from "../storage.js";

export const ticketTypes = [
  ["account", "Cuenta o contraseña"],
  ["launcher", "Launcher o conexión"],
  ["character", "Personaje o ítem"],
  ["purchase", "Compra o donación"],
  ["player", "Reportar jugador"],
  ["exploit", "Reportar exploit privado"],
  ["appeal", "Apelar sanción"],
  ["other", "Otro problema"],
] as const;

const cooldowns = new Map<string, number>();

export function ticketPanel() {
  const rows = [ticketTypes.slice(0, 4), ticketTypes.slice(4)].map((group) =>
    new ActionRowBuilder<ButtonBuilder>().addComponents(
      ...group.map(([key, label]) =>
        new ButtonBuilder()
          .setCustomId(`ticket:open:${key}`)
          .setLabel(label)
          .setStyle(key === "exploit" ? ButtonStyle.Danger : ButtonStyle.Secondary),
      ),
    ),
  );
  return {
    embeds: [
      new EmbedBuilder()
        .setColor(0xd5b478)
        .setTitle("Soporte de Megarax Network")
        .setDescription(
          "Selecciona una categoría. El canal será privado y solo visible para ti y el equipo correspondiente.",
        ),
    ],
    components: rows,
  };
}

export async function handleTicketButton(
  interaction: ButtonInteraction,
  manifest: Manifest,
): Promise<boolean> {
  if (!interaction.customId.startsWith("ticket:")) return false;
  if (!interaction.guild || !interaction.member) return true;
  const [, action, value] = interaction.customId.split(":");
  if (action === "open" && value) await openTicket(interaction, manifest, value);
  if (action === "claim") await claimTicket(interaction);
  if (action === "close") await closeTicket(interaction, manifest);
  return true;
}

async function openTicket(
  interaction: ButtonInteraction,
  manifest: Manifest,
  type: string,
): Promise<void> {
  const guild = interaction.guild;
  if (!guild) return;
  const key = `${guild.id}:${interaction.user.id}:${type}`;
  const now = Date.now();
  if ((cooldowns.get(key) ?? 0) > now - 60_000) {
    await interaction.reply({
      content: "Espera un minuto antes de intentar abrir otro ticket.",
      ephemeral: true,
    });
    return;
  }
  const duplicate = guild.channels.cache.find(
    (channel) =>
      channel.type === ChannelType.GuildText &&
      channel.topic?.includes(`MDM-TICKET user=${interaction.user.id} type=${type} status=open`),
  );
  if (duplicate) {
    await interaction.reply({
      content: `Ya tienes un ticket abierto de este tipo: <#${duplicate.id}>`,
      ephemeral: true,
    });
    return;
  }
  cooldowns.set(key, now);
  await interaction.deferReply({ ephemeral: true });
  const state = await readState(guild.id);
  const supportCategory = state.resources["category:support"]?.id;
  const staffRoleIds = manifest.roles
    .filter((role) => ["administrator", "game_master", "moderator", "support"].includes(role.key))
    .map((role) => state.resources[`role:${role.key}`]?.id)
    .filter((id): id is string => Boolean(id));
  const channel = await guild.channels.create({
    name: `ticket-${type}-${interaction.user.username}`.slice(0, 90),
    type: ChannelType.GuildText,
    parent: supportCategory ?? null,
    topic: `MDM-TICKET user=${interaction.user.id} type=${type} status=open opened=${new Date().toISOString()}`,
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
      {
        id: interaction.user.id,
        allow: [
          PermissionFlagsBits.ViewChannel,
          PermissionFlagsBits.SendMessages,
          PermissionFlagsBits.ReadMessageHistory,
          PermissionFlagsBits.AttachFiles,
        ],
      },
      ...staffRoleIds.map((id) => ({
        id,
        allow: [
          PermissionFlagsBits.ViewChannel,
          PermissionFlagsBits.SendMessages,
          PermissionFlagsBits.ReadMessageHistory,
          PermissionFlagsBits.ManageMessages,
        ],
      })),
    ],
    reason: `Megarax Discord Manager: ticket ${type} abierto por ${interaction.user.id}`,
  });
  await channel.send({
    embeds: [
      new EmbedBuilder()
        .setColor(0xd5b478)
        .setTitle(ticketTypes.find(([key]) => key === type)?.[1] ?? "Ticket")
        .setDescription(
          `Abierto por <@${interaction.user.id}>. Describe el problema sin publicar contraseñas ni datos de pago.`,
        )
        .setFooter({ text: `Categoría: ${type}` })
        .setTimestamp(),
    ],
    components: [
      new ActionRowBuilder<ButtonBuilder>().addComponents(
        new ButtonBuilder()
          .setCustomId("ticket:claim")
          .setLabel("Reclamar")
          .setStyle(ButtonStyle.Primary),
        new ButtonBuilder()
          .setCustomId("ticket:close")
          .setLabel("Cerrar")
          .setStyle(ButtonStyle.Danger),
      ),
    ],
  });
  await interaction.editReply(`Ticket creado: <#${channel.id}>`);
}

async function claimTicket(interaction: ButtonInteraction): Promise<void> {
  const member = interaction.member as GuildMember;
  if (!member.permissions.has(PermissionFlagsBits.ManageMessages)) {
    await interaction.reply({
      content: "No tienes permiso para reclamar tickets.",
      ephemeral: true,
    });
    return;
  }
  const channel = interaction.channel as TextChannel;
  if (!channel.topic?.startsWith("MDM-TICKET")) return;
  await channel.setTopic(
    `${channel.topic} claimed=${interaction.user.id}`,
    "Megarax Discord Manager: ticket reclamado",
  );
  await interaction.reply(`Ticket reclamado por <@${interaction.user.id}>.`);
}

async function closeTicket(interaction: ButtonInteraction, manifest: Manifest): Promise<void> {
  const channel = interaction.channel as TextChannel;
  if (!channel.topic?.startsWith("MDM-TICKET")) return;
  await interaction.deferReply({ ephemeral: true });
  const messages = await channel.messages.fetch({ limit: 100 });
  const transcript = [...messages.values()]
    .reverse()
    .map(
      (message) =>
        `[${message.createdAt.toISOString()}] ${message.author.tag}: ${message.cleanContent}`,
    )
    .join("\n");
  await mkdir("transcripts", { recursive: true });
  const file = `transcripts/${channel.id}-${Date.now()}.txt`;
  await writeFile(file, transcript, "utf8");
  const state = await readState(channel.guild.id);
  const logId = state.resources["channel:tickets_log"]?.id;
  const log = logId ? channel.guild.channels.cache.get(logId) : undefined;
  if (log?.isTextBased() && !log.isThread()) {
    await log.send({
      content: `Ticket cerrado: **${channel.name}** por <@${interaction.user.id}>`,
      files: [{ attachment: file, name: `${channel.name}.txt` }],
    });
  }
  await interaction.editReply("Transcripción creada. El ticket se cerrará en 5 segundos.");
  setTimeout(() => void channel.delete("Megarax Discord Manager: ticket cerrado"), 5_000);
  void manifest;
}
