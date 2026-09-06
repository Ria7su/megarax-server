import type { Guild } from "discord.js";
import type { Snapshot } from "../types.js";

export async function captureSnapshot(guild: Guild): Promise<Snapshot> {
  await Promise.all([guild.roles.fetch(), guild.channels.fetch()]);
  return {
    createdAt: new Date().toISOString(),
    guild: { id: guild.id, name: guild.name, ownerId: guild.ownerId },
    roles: guild.roles.cache
      .map((role) => ({
        id: role.id,
        name: role.name,
        position: role.position,
        permissions: role.permissions.bitfield.toString(),
        managed: role.managed,
      }))
      .sort((a, b) => b.position - a.position),
    channels: guild.channels.cache
      .filter((channel) => !channel.isThread())
      .map((channel) => ({
        id: channel.id,
        name: channel.name,
        type: channel.type,
        position: channel.position,
        parentId: channel.parentId,
        permissionOverwrites: channel.permissionOverwrites.cache.map((overwrite) => ({
          id: overwrite.id,
          allow: overwrite.allow.bitfield.toString(),
          deny: overwrite.deny.bitfield.toString(),
          type: overwrite.type,
        })),
      }))
      .sort((a, b) => a.position - b.position),
  };
}
