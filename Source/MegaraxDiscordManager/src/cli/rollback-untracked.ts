import { readFile, readdir } from "node:fs/promises";
import { ChannelType } from "discord.js";
import { loadManifest } from "../config.js";
import type { Snapshot } from "../types.js";
import { withGuild } from "./shared.js";

await withGuild(async ({ guild }) => {
  const manifest = await loadManifest();
  const backupFiles = (await readdir("backups"))
    .filter((file) => file.startsWith("guild-") && file.endsWith(".json"))
    .sort()
    .reverse();
  const latestBackup = backupFiles[0];
  if (!latestBackup) throw new Error("No existe un snapshot para realizar el rollback.");

  const snapshot = JSON.parse(await readFile(`backups/${latestBackup}`, "utf8")) as Snapshot;
  const knownRoleIds = new Set(snapshot.roles.map((role) => role.id));
  const knownChannelIds = new Set(snapshot.channels.map((channel) => channel.id));
  const managedRoleNames = new Set(manifest.roles.map((role) => role.name));
  const managedChannelNames = new Set(
    manifest.categories.flatMap((category) => [
      category.name,
      ...category.channels.map((channel) => channel.name),
    ]),
  );

  await guild.channels.fetch();
  const newChannels = guild.channels.cache
    .filter((channel) => !knownChannelIds.has(channel.id) && managedChannelNames.has(channel.name))
    .sort(
      (left, right) =>
        Number(left.type === ChannelType.GuildCategory) -
        Number(right.type === ChannelType.GuildCategory),
    );
  for (const channel of newChannels.values()) {
    try {
      await channel.delete("Rollback seguro de instalación incompleta de Megarax");
      console.log(`Canal revertido: ${channel.name}`);
    } catch {
      console.warn(`Requiere eliminación manual: ${channel.name} (${channel.id})`);
    }
  }

  await guild.roles.fetch();
  const newRoles = guild.roles.cache.filter(
    (role) => !knownRoleIds.has(role.id) && managedRoleNames.has(role.name),
  );
  for (const role of newRoles.values()) {
    try {
      await role.delete("Rollback seguro de instalación incompleta de Megarax");
      console.log(`Rol revertido: ${role.name}`);
    } catch {
      console.warn(`Requiere eliminación manual: ${role.name} (${role.id})`);
    }
  }
});
