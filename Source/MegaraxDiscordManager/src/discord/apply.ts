import { readFile } from "node:fs/promises";
import {
  ChannelType,
  Colors,
  OverwriteType,
  PermissionFlagsBits,
  type CategoryChannel,
  type Guild,
  type GuildBasedChannel,
} from "discord.js";
import type { Manifest } from "../config.js";
import { rolePermissions } from "./permissions.js";
import { fingerprint, readState, writeState } from "../storage.js";
import type { ChangePlan, ManagedState } from "../types.js";

const reason = "Megarax Discord Manager: apply de plan validado";

export async function applyPlan(guild: Guild, manifest: Manifest, plan: ChangePlan): Promise<void> {
  if (!plan.validated || plan.actions.some((action) => action.action === "conflict")) {
    throw new Error("El plan contiene conflictos o no está validado.");
  }
  if (plan.guildId !== guild.id) throw new Error("El plan pertenece a otro servidor.");
  if (plan.manifestHash !== fingerprint(manifest)) {
    throw new Error("El manifiesto cambió después del dry-run. Genera un plan nuevo.");
  }

  const state = await readState(guild.id);
  await guild.roles.fetch();
  await guild.channels.fetch();

  for (const spec of manifest.roles) {
    const key = `role:${spec.key}`;
    const tracked = state.resources[key];
    const existing = tracked ? guild.roles.cache.get(tracked.id) : undefined;
    const desired = {
      name: spec.name,
      color: spec.color,
      permissions: spec.permissions,
      manualPermissions: spec.manualPermissions,
    };
    const payload = {
      name: spec.name,
      color: spec.color ? Number.parseInt(spec.color.slice(1), 16) : Colors.Default,
      permissions: rolePermissions(spec),
      reason,
    };
    const role = existing ? await existing.edit(payload) : await guild.roles.create(payload);
    track(state, key, role.id, "role", desired);
    state.updatedAt = new Date().toISOString();
    await writeState(state);
  }

  for (const spec of manifest.categories) {
    const key = `category:${spec.key}`;
    const existing = channelFromState(guild, state, key);
    const desired = {
      name: spec.name,
      staffOnly: spec.staffOnly,
      everyoneVisible: spec.everyoneVisible,
    };
    const permissionOverwrites = categoryOverwrites(
      guild,
      manifest,
      state,
      spec.staffOnly,
      spec.everyoneVisible,
    );
    let category: CategoryChannel;
    if (existing?.type === ChannelType.GuildCategory) {
      category = await existing.edit({ name: spec.name, permissionOverwrites, reason });
    } else {
      category = await guild.channels.create({
        name: spec.name,
        type: ChannelType.GuildCategory,
        permissionOverwrites,
        reason,
      });
    }
    track(state, key, category.id, "category", desired);
    state.updatedAt = new Date().toISOString();
    await writeState(state);

    for (const channelSpec of spec.channels) {
      const channelKey = `channel:${channelSpec.key}`;
      const channelExisting = channelFromState(guild, state, channelKey);
      const type = channelType(channelSpec.type);
      const channelDesired = {
        name: channelSpec.name,
        type: channelSpec.type,
        parentKey: key,
        readOnly: channelSpec.readOnly,
        tags: channelSpec.tags ?? [],
        content: channelSpec.content ?? null,
        permissionPolicyVersion: 2,
      };
      const overwrites = permissionOverwrites.map((overwrite) =>
        overwrite.id === guild.roles.everyone.id && channelSpec.readOnly
          ? {
              ...overwrite,
              deny: [
                ...("deny" in overwrite ? overwrite.deny : []),
                PermissionFlagsBits.SendMessages,
                PermissionFlagsBits.CreatePublicThreads,
              ],
            }
          : overwrite,
      );
      let channel: GuildBasedChannel;
      if (channelExisting?.type === type) {
        channel = await channelExisting.edit({
          name: channelSpec.name,
          parent: category.id,
          permissionOverwrites: overwrites,
          reason,
        });
      } else {
        channel = await guild.channels.create({
          name: channelSpec.name,
          type,
          parent: category.id,
          permissionOverwrites: overwrites,
          availableTags: channelSpec.tags?.map((name) => ({ name })) ?? [],
          reason,
        });
      }
      track(state, channelKey, channel.id, "channel", channelDesired);
      state.updatedAt = new Date().toISOString();
      await writeState(state);
    }
  }
  state.updatedAt = new Date().toISOString();
  await writeState(state);
}

export async function publishManagedContent(guild: Guild, manifest: Manifest): Promise<number> {
  await guild.channels.fetch();
  const state = await readState(guild.id);
  let published = 0;
  for (const category of manifest.categories) {
    for (const channel of category.channels) {
      if (!channel.content) continue;
      const existing = channelFromState(guild, state, `channel:${channel.key}`);
      if (!existing?.isTextBased() || existing.isThread()) continue;
      const markdown = await readFile(`content/${channel.content}`, "utf8");
      const rendered = renderContent(markdown, manifest);
      const messages = await existing.messages.fetch({ limit: 20 });
      const managed = messages.find(
        (message) =>
          message.author.id === guild.members.me?.id &&
          message.embeds[0]?.footer?.text === `MDM:${channel.key}`,
      );
      const embed = {
        color: 0xd5b478,
        description: rendered.slice(0, 4096),
        footer: { text: `MDM:${channel.key}` },
        timestamp: new Date().toISOString(),
      };
      if (managed) await managed.edit({ embeds: [embed] });
      else await existing.send({ embeds: [embed] });
      published += 1;
    }
  }
  return published;
}

function renderContent(markdown: string, manifest: Manifest): string {
  const statusVars: Record<string, string> = {
    LOGIN_SERVER: manifest.status.loginServer ?? "Manual",
    GAME_SERVER: manifest.status.gameServer ?? "Manual",
    WEBSITE: manifest.status.website ?? "Manual",
    LAUNCHER: manifest.status.launcher ?? "Manual",
    PLAYERS_ONLINE: manifest.status.playersOnline ?? "0",
    LAST_RESTART: manifest.status.lastRestart ?? "Pendiente",
    NEXT_MAINTENANCE: manifest.status.nextMaintenance ?? "Pendiente",
  };
  return Object.entries({ ...manifest.placeholders, ...statusVars }).reduce(
    (text, [key, value]) => text.replaceAll(`{{${key}}}`, value),
    markdown,
  );
}

function channelType(
  type: Manifest["categories"][number]["channels"][number]["type"],
):
  | ChannelType.GuildText
  | ChannelType.GuildAnnouncement
  | ChannelType.GuildForum
  | ChannelType.GuildMedia
  | ChannelType.GuildVoice {
  switch (type) {
    case "text":
      return ChannelType.GuildText;
    case "announcement":
      // Announcement channels require Discord Community mode. A normal text
      // channel preserves the content and permissions on private guilds.
      return ChannelType.GuildText;
    case "forum":
      return ChannelType.GuildForum;
    case "media":
      // Media channels also depend on Discord Community features.
      return ChannelType.GuildText;
    case "voice":
      return ChannelType.GuildVoice;
  }
}

function channelFromState(
  guild: Guild,
  state: ManagedState,
  key: string,
): GuildBasedChannel | undefined {
  const id = state.resources[key]?.id;
  return id ? guild.channels.cache.get(id) : undefined;
}

function track(
  state: ManagedState,
  key: string,
  id: string,
  kind: "role" | "category" | "channel",
  desired: unknown,
): void {
  state.resources[key] = { id, kind, fingerprint: fingerprint(desired) };
}

function categoryOverwrites(
  guild: Guild,
  manifest: Manifest,
  state: ManagedState,
  staffOnly: boolean,
  everyoneVisible: boolean,
) {
  const botAccess = {
    id: guild.client.user.id,
    type: OverwriteType.Member,
    allow: [
      PermissionFlagsBits.ViewChannel,
      PermissionFlagsBits.SendMessages,
      PermissionFlagsBits.ReadMessageHistory,
      PermissionFlagsBits.ManageChannels,
    ],
  };
  if (staffOnly) {
    const staffIds = manifest.roles
      .filter((role) => role.group === "staff")
      .map((role) => state.resources[`role:${role.key}`]?.id)
      .filter((id): id is string => Boolean(id));
    return [
      {
        id: guild.roles.everyone.id,
        type: OverwriteType.Role,
        deny: [PermissionFlagsBits.ViewChannel],
      },
      botAccess,
      ...staffIds.map((id) => ({
        id,
        type: OverwriteType.Role,
        allow: [
          PermissionFlagsBits.ViewChannel,
          PermissionFlagsBits.SendMessages,
          PermissionFlagsBits.ReadMessageHistory,
        ],
      })),
    ];
  }
  if (everyoneVisible) {
    return [
      {
        id: guild.roles.everyone.id,
        type: OverwriteType.Role,
        allow: [PermissionFlagsBits.ViewChannel],
      },
      botAccess,
    ];
  }
  const playerId = state.resources["role:player"]?.id;
  return [
    {
      id: guild.roles.everyone.id,
      type: OverwriteType.Role,
      deny: [PermissionFlagsBits.ViewChannel],
    },
    botAccess,
    ...(playerId
      ? [{ id: playerId, type: OverwriteType.Role, allow: [PermissionFlagsBits.ViewChannel] }]
      : []),
  ];
}
